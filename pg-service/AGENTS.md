# AGENTS.md — pg-service (Payment Gateway)

## Role

pg-service는 **가맹점(merchant)과 카드사(card-authorization) 사이의 결제 게이트웨이**이다.
현재는 stateless 프록시 수준이며, 본격적인 PG사 역할을 수행하도록 고도화하는 것이 목표이다.

## Current State

### Package Structure

```
dev.pg
├── PgServiceApplication.java       ← @SpringBootApplication + @EnableFeignClients
├── controller/
│   └── PgApprovalController.java   ← POST /api/pg/approve, 에러 핸들링
├── service/
│   └── PgApprovalService.java      ← 검증 → PG TX ID 생성 → 카드사 호출 → 응답 매핑
├── client/
│   ├── CardAuthorizationServiceClient.java  ← @FeignClient(name="card-authorization-service")
│   └── CardAuthorizationClient.java         ← Wrapper, 에러 시 fallback 응답 반환
└── dto/
    ├── MerchantApprovalRequest.java   ← 가맹점 → PG 요청
    ├── MerchantApprovalResponse.java  ← PG → 가맹점 응답
    ├── CardAuthorizationRequest.java  ← PG → 카드사 요청
    └── CardAuthorizationResponse.java ← 카드사 → PG 응답
```

### Current Flow

1. `PgApprovalController.approve()` 수신
2. `PgApprovalService.validateRequest()` — null/blank/amount 기본 검증
3. `PgApprovalService.generatePgTransactionId()` — `PG` + yyyyMMddHHmmss + UUID(6)
4. DTO 변환: `MerchantApprovalRequest` → `CardAuthorizationRequest`
5. `CardAuthorizationClient.authorize()` — FeignClient 호출 + try-catch fallback
6. `PgApprovalService.mapToMerchantResponse()` — 카드사 응답 → 가맹점 응답 매핑
7. 컨트롤러에서 최종 응답 반환

### Dependencies

```gradle
// build.gradle — Spring Boot 4.0.3, Spring Cloud 2025.1.0
spring-boot-starter-webmvc
spring-cloud-starter-netflix-eureka-client
spring-cloud-starter-openfeign
springdoc-openapi-starter-webmvc-ui:3.0.2
lombok
```

### What's Missing (Current Limitations)

- **DB 없음** → 거래 원장(Transaction Ledger) 관리 불가
- **단일 카드사 연동** → 다중 카드사/VAN 라우팅 없음
- **서킷 브레이커/재시도 없음** → 카드사 장애 시 전파
- **보안 없음** → Spring Security 미적용, API Key/인증 없음
- **비동기 처리 없음** → 정산, 알림 등 후속 처리 불가
- **모니터링 없음** → 거래 통계, 장애 감지 불가
- **거래 멱등성 없음** → 동일 요청 중복 승인 가능성

## Upstream API (merchant-service → pg-service)

```
POST /api/pg/approve

수신 DTO: MerchantApprovalRequest
- merchantTransactionId: String (필수, 가맹점 고유 번호)
- merchantId: String (필수)
- cardNumber: String (필수)
- expiryDate: String (필수, "YYMM" 형식)
- amount: BigDecimal (필수, > 0)
- currency: String (필수, "KRW")
- mti: String ("0100" = 승인 요청)
- processingCode: String ("000000")
- transmissionDateTime: String ("yyyyMMddHHmmss")
- stan: String (6자리, System Trace Audit Number)

반환 DTO: MerchantApprovalResponse
- merchantTransactionId: String
- pgTransactionId: String
- approved: boolean
- responseCode: String ("00"=승인, "96"=시스템오류, 등)
- message: String
- approvalNumber: String (승인 시)
- approvedAt: LocalDateTime
```

## Downstream API (pg-service → card-authorization-service)

```
POST /api/authorization/request

전송 DTO: CardAuthorizationRequest
- transactionId: String (PG TX ID)
- cardNumber: String
- amount: BigDecimal
- merchantId: String
- terminalId: String (nullable)
- pin: String (nullable)

수신 DTO: CardAuthorizationResponse
- transactionId: String
- approvalNumber: String
- responseCode: String
- message: String
- amount: BigDecimal
- authorizationDate: LocalDateTime
- approved: boolean
```

## Upgrade Roadmap

아래는 단계별 고도화 방향이다. 각 단계는 이전 단계 완료 후 진행한다.

### Phase 1: Foundation — DB + Transaction Ledger

- MySQL 연동 (pg_db 추가, docker/mysql/init 스크립트 수정)
- Transaction 엔티티 설계 (거래 원장)
  - pgTransactionId (PK), merchantTransactionId, merchantId
  - cardNumber (마스킹 저장), amount, currency
  - status (PENDING → APPROVED / FAILED / TIMEOUT)
  - requestedAt, respondedAt, responseCode, approvalNumber
- 멱등성 보장: merchantTransactionId 중복 요청 시 기존 결과 반환
- application.yaml에 datasource, JPA 설정 추가
- docker-compose.yml에 pg-service의 mysql depends_on 추가

### Phase 2: Resilience — Circuit Breaker + Retry

- Resilience4j 도입 (spring-cloud-starter-circuitbreaker-resilience4j)
- 카드사 호출에 CircuitBreaker + Retry + TimeLimiter 적용
- Fallback 전략: CircuitBreaker OPEN 시 거래 상태를 TIMEOUT으로 기록
- 카드사 응답 지연/실패 시의 보상 처리 설계

### Phase 3: Multi-Acquirer Routing

- 다중 카드사/VAN 라우팅 전략
- 카드 BIN(앞 6자리) 기반 카드사 식별
- 라우팅 설정의 외부화 (application.yaml 또는 DB)
- FeignClient를 동적으로 선택하는 Router 컴포넌트

### Phase 4: Security + Monitoring

- Spring Security + API Key 기반 가맹점 인증
- 가맹점 등록/관리 (merchantId ↔ API Key 매핑)
- Actuator + Micrometer 메트릭 노출
- 거래 통계 API (일별/가맹점별 승인율, 거래량)

### Phase 5: Async Processing + Settlement

- 승인 완료 후 이벤트 발행 (Spring ApplicationEvent 또는 Kafka 검토)
- 정산 배치 처리
- 알림 서비스 연동 (webhook 등)

## Testing Strategy

### Unit Tests

- `PgApprovalServiceTest` — 이미 존재, Mockito 기반
- 새 기능 추가 시 동일 패턴으로 Service 레이어 테스트 작성
- CardAuthorizationClient의 fallback 동작 검증

### Integration Tests

- `@SpringBootTest` + WireMock으로 카드사 API 모킹
- DB 연동 후 `@DataJpaTest`로 Repository 레이어 테스트
- H2 in-memory DB 사용 (test profile)

### E2E Tests

- docker-compose 전체 스택 기동 후 api-gateway를 통한 엔드투엔드 테스트
- 정상 승인, 잔액 부족, 카드 정지, 시스템 오류 시나리오

## Design Principles

1. **거래 원장 무결성**: 모든 거래는 DB에 기록된 후 외부 호출. 실패 시에도 이력 보존
2. **멱등성**: 동일 merchantTransactionId 재요청 시 기존 결과 반환, 중복 승인 방지
3. **Fail-safe**: 카드사 통신 실패 시 거래를 TIMEOUT으로 기록하고, 추후 조회/재시도 가능하게
4. **마스킹**: cardNumber는 앞 6자리(BIN) + 뒤 4자리만 보존, 나머지 마스킹
5. **Wrapper Client 패턴 유지**: FeignClient 직접 노출 금지, 에러 핸들링 래퍼 유지
6. **ISO 8583 호환**: MTI, processingCode, STAN 등 전문 필드 유지 및 활용
