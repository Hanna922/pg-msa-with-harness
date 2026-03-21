# AGENTS.md — Payment Gateway MSA Project

## Project Overview

카드 결제 승인 처리를 위한 MSA 시스템. **pg-service를 결제 게이트웨이(Payment Gateway)로 고도화**하는 것이 핵심 목표이다.

## Scope Constraint ⚠️

- **수정 대상: `pg-service` ONLY**
- `card-authorization-service`, `bank-service`, `merchant-service`는 테스트용 스텁 서비스이다
- 스텁 서비스의 API 스펙을 변경하지 않는다 (pg-service가 이 API에 맞춰야 한다)
- `eureka-server`, `api-gateway`는 인프라 컴포넌트로 현행 유지한다

## Architecture — Service Call Flow

```
Client
  │
  ▼
api-gateway (:8000)          ← Spring Cloud Gateway WebMVC, /api/merchant/** 라우팅
  │
  ▼
merchant-service (:7070)     ← 가맹점, JPA + MySQL(merchant_db), Payment 엔티티
  │ FeignClient: PgServiceClient → POST /api/pg/approve
  ▼
pg-service (:8081)           ← ★ 고도화 대상, 현재 stateless 프록시
  │ FeignClient: CardAuthorizationServiceClient → POST /api/authorization/request
  ▼
card-authorization-service (:9090) ← 카드사, JPA + MySQL(card_authorization_db)
  │ FeignClient + RestClient: BankServiceClient
  ▼
bank-service (:8080)         ← 은행, JPA + MySQL(bank_db), 비관적 락
```

## Service Registry

모든 서비스는 **Eureka Server (:8761)**에 등록되며, FeignClient는 서비스 이름(`@FeignClient(name = "...")`)으로 Eureka를 통해 인스턴스를 조회한다.

## Tech Stack Summary

| Service                    | Spring Boot | Spring Cloud | DB                            | 비고              |
| -------------------------- | ----------- | ------------ | ----------------------------- | ----------------- |
| eureka-server              | 4.0.3       | 2025.1.0     | -                             | 서비스 디스커버리 |
| api-gateway                | 4.0.3       | 2025.1.0     | -                             | Gateway WebMVC    |
| pg-service                 | 4.0.3       | 2025.1.0     | **없음** (고도화 시 추가)     | ★ 고도화 대상     |
| merchant-service           | 4.0.3       | 2025.1.0     | MySQL (merchant_db)           | 테스트 스텁       |
| card-authorization-service | 3.2.0       | 2023.0.4     | MySQL (card_authorization_db) | 테스트 스텁       |
| bank-service               | 3.2.0       | 2023.0.4     | MySQL (bank_db)               | 테스트 스텁       |

> **주의**: pg-service(4.0.3)와 스텁 서비스(3.2.0) 간 Spring Boot 버전이 다르다. 런타임 호환성 문제는 없지만 공통 라이브러리 확장 시 유의.

## Shared Module

`common/` — bank-service, card-authorization-service가 공유하는 enum 모듈 (AccountType, AccountStatus, MappingStatus). 순수 Java, Spring 의존성 없음. pg-service는 현재 사용하지 않음.

## Docker Environment

```bash
docker-compose up --build    # 전체 스택 기동
```

- 단일 `docker-compose.yml`, `msa-net` 브릿지 네트워크
- MySQL 8.0 단일 인스턴스, `docker/mysql/init/01-init-databases.sql`에서 3개 DB 초기 생성
- MySQL healthcheck로 DB 의존 서비스 시작 순서 보장
- 외부 노출 포트: 8000 (api-gateway), 8761 (eureka)

## API Specifications (Stub Services)

### merchant-service → pg-service

```
POST /api/pg/approve
Content-Type: application/json

Request (PgAuthRequest / MerchantApprovalRequest):
{
  "merchantTransactionId": "M...",      // 가맹점 결제 고유 번호
  "merchantId": "MERCHANT-001",
  "cardNumber": "4111111111111111",
  "expiryDate": "2712",
  "amount": 10000,                      // ⚠️ merchant: Integer, pg: BigDecimal
  "currency": "KRW",
  "mti": "0100",                        // ISO 8583 Message Type Indicator
  "processingCode": "000000",
  "transmissionDateTime": "20260319153000",
  "stan": "123456"                      // System Trace Audit Number
}

Response (PgAuthResponse / MerchantApprovalResponse):
{
  "merchantTransactionId": "M...",
  "pgTransactionId": "PG20260319153000ABCDEF",
  "approved": true,
  "responseCode": "00",
  "message": "Approved",
  "approvalNumber": "12345678",
  "approvedAt": "2026-03-19T15:30:05"
}
```

### pg-service → card-authorization-service

```
POST /api/authorization/request
Content-Type: application/json

Request (CardAuthorizationRequest):
{
  "transactionId": "PG20260319153000ABCDEF",
  "cardNumber": "4111111111111111",
  "amount": 10000,                      // BigDecimal
  "merchantId": "MERCHANT-001",
  "terminalId": null,
  "pin": null
}

Response (CardAuthorizationResponse → AuthorizationResponse):
{
  "transactionId": "PG20260319153000ABCDEF",
  "approvalNumber": "12345678",
  "responseCode": "00",
  "message": "승인",
  "amount": 10000,
  "authorizationDate": "2026-03-19T15:30:05",
  "approved": true
}
```

## Response Code System

| Code | Meaning             | Source                                    |
| ---- | ------------------- | ----------------------------------------- |
| `00` | 승인                | card-authorization-service                |
| `14` | 카드 정지/분실/해지 | card-authorization-service                |
| `51` | 잔액 부족           | bank-service → card-authorization-service |
| `54` | 유효기간 만료       | card-authorization-service                |
| `55` | PIN 오류            | card-authorization-service                |
| `61` | 한도 초과           | card-authorization-service                |
| `96` | 시스템 오류         | 전 서비스 공통                            |

## Coding Conventions

- 패키지 구조: `dev.pg.*` (pg-service), `com.card.payment.*` (card/bank)
- DTO: Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- Feign: `@FeignClient(name = "서비스명")` + Eureka 서비스 디스커버리
- Wrapper Client 패턴: FeignClient 인터페이스를 직접 노출하지 않고, 에러 핸들링을 감싸는 `*Client` 컴포넌트를 두는 것이 현재 패턴 (예: `CardAuthorizationClient` wraps `CardAuthorizationServiceClient`)
- 거래 ID 포맷: `PG` + yyyyMMddHHmmss + UUID 6자리 (대문자)

## Known Issues

1. **금액 타입 불일치**: merchant-service의 `PgAuthRequest.amount`는 `Integer`, pg-service의 `MerchantApprovalRequest.amount`는 `BigDecimal`
2. **FakePgController**: merchant-service에 pg-service 목업 컨트롤러가 존재 (독립 테스트용)
3. **pg-service에 DB 없음**: 거래 이력 추적 불가
4. **pg-service에 보안 없음**: Spring Security 미적용, 인증/인가 없음
5. **에러 처리 미흡**: try-catch → fallback 응답 수준, 서킷 브레이커/재시도 없음
