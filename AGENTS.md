# AGENTS.md — Payment Gateway MSA Project

## Project Overview

카드 결제 승인 처리를 위한 MSA 시스템. **pg-service를 결제 게이트웨이(Payment Gateway)로 고도화**하는 것이 핵심 목표이다.

## Scope Constraint ⚠️

- **수정 대상: `pg-service` ONLY** (Phase 6 BIN 기반 라우팅 구현)
- **신규 생성 대상: `card-authorization-service-2`** (기존 card-authorization-service 복제, 테스트용 second acquirer)
- `card-authorization-service`, `bank-service`, `merchant-service`, `api-gateway`, `eureka-server`는 기존 코드 수정 금지
- 인프라 파일(`docker-compose.yml`, `docker/mysql/init/`)은 서비스 추가에 필요한 최소 변경만 허용

## Architecture — Service Call Flow (Phase 6 Target)

```
Client
  │
  ▼
api-gateway (:8000)
  │
  ▼
merchant-service (:7070)
  │  POST /api/pg/approve
  ▼
pg-service (:8081)  ← ★ BIN-based routing
  │
  ├─ BIN 4xxx (Visa) ──────────────► card-authorization-service   (:9090)  [Acquirer A]
  │                                        │
  │                                        ▼
  │                                   bank-service (:8080)
  │
  └─ BIN 5xxx/3xxx/6xxx/35xx ──────► card-authorization-service-2 (:9091)  [Acquirer B]
                                           │
                                           ▼
                                      bank-service (:8080)  ← 동일 bank-service 공유
```

## Service Registry (Eureka :8761)

| Service Name (Eureka)            | Port     | Role                                              | DB                          |
| -------------------------------- | -------- | ------------------------------------------------- | --------------------------- |
| eureka-server                    | 8761     | 서비스 디스커버리                                 | -                           |
| api-gateway                      | 8000     | Gateway WebMVC                                    | -                           |
| merchant-service                 | 7070     | 가맹점 (스텁)                                     | merchant_db                 |
| **pg-service**                   | **8081** | **PG 게이트웨이 (고도화 대상)**                   | **pg_db**                   |
| card-authorization-service       | 9090     | Acquirer A — Visa (스텁)                          | card_authorization_db       |
| **card-authorization-service-2** | **9091** | **Acquirer B — MC/Amex/Discover/JCB (신규 스텁)** | **card_authorization_db_2** |
| bank-service                     | 8080     | 은행 (스텁)                                       | bank_db                     |

## BIN Routing Rule

카드 번호 앞자리(BIN prefix)로 카드 브랜드를 식별하고, 브랜드별로 acquirer를 선택한다.

| BIN Prefix         | Card Brand | Acquirer | Eureka Service Name          |
| ------------------ | ---------- | -------- | ---------------------------- |
| `4`                | Visa       | **A**    | card-authorization-service   |
| `5`                | Mastercard | **B**    | card-authorization-service-2 |
| `3` (but not `35`) | Amex       | **B**    | card-authorization-service-2 |
| `35`               | JCB        | **B**    | card-authorization-service-2 |
| `6`                | Discover   | **B**    | card-authorization-service-2 |

> 매칭 순서: longest prefix first (`35` → `3` → `4` → `5` → `6`)

## Test Card Distribution

### Acquirer A (card-authorization-service) — Visa Only

| Card Number      | Brand | Type  | Status                   | Customer |
| ---------------- | ----- | ----- | ------------------------ | -------- |
| 4111111111111111 | Visa  | DEBIT | ACTIVE                   | CUST-001 |
| 4012888888881881 | Visa  | DEBIT | ACTIVE (expired 2024-12) | CUST-007 |

### Acquirer B (card-authorization-service-2) — MC, Amex, Discover, JCB

| Card Number      | Brand      | Type   | Status    | Customer |
| ---------------- | ---------- | ------ | --------- | -------- |
| 5555555555554444 | Mastercard | DEBIT  | ACTIVE    | CUST-002 |
| 378282246310005  | Amex       | DEBIT  | ACTIVE    | CUST-003 |
| 6011111111111117 | Discover   | CREDIT | ACTIVE    | CUST-004 |
| 3530111333300000 | JCB        | CREDIT | ACTIVE    | CUST-005 |
| 5105105105105100 | Mastercard | DEBIT  | SUSPENDED | CUST-006 |

### bank-service — 변경 없음

bank-service는 두 acquirer가 공유한다. card_account_mappings에 7장 전체가 매핑되어 있으므로, 어느 acquirer가 호출하든 잔액 조회/출금이 가능하다.

## API Specifications

### pg-service → card-authorization-service / card-authorization-service-2 (동일 API)

두 acquirer는 **동일한 API 스펙**을 공유한다. pg-service가 BIN에 따라 어느 서비스로 보낼지만 결정한다.

```
POST /api/authorization/request
Request:  { transactionId, cardNumber, amount(BigDecimal), merchantId, terminalId, pin }
Response: { transactionId, approvalNumber, responseCode, message, amount, authorizationDate, approved }
```

## Completed Phases

1. 거래 원장 + 멱등성 기반
2. 승인 흐름 책임 분리 (Facade 패턴)
3. 외부 카드사 연동 안정화 (timeout / retry / circuit breaker)
4. PG 내부 예외 표준화 + 전역 처리
5. 라우팅 추상화 (RoutingPolicy / AcquirerRoutingService)
6. **BIN 기반 다중 Acquirer 라우팅** ← 현재 진행

## Coding Conventions

- 패키지: `dev.pg.*` (pg-service), `com.card.payment.*` (card/bank)
- DTO: Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- Feign: `@FeignClient(name = "서비스명")` + Eureka 서비스 디스커버리
- Wrapper Client 패턴: FeignClient 인터페이스를 직접 노출하지 않고, 에러 핸들링 래퍼 사용
- 거래 ID: `PG` + yyyyMMddHHmmss + UUID 6자리 (대문자)

## Known Issues

1. 금액 타입 불일치: merchant-service(Integer) vs pg-service(BigDecimal)
2. FakePgController: merchant-service에 pg-service 목업 존재 (독립 테스트용)
