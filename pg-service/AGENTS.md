# AGENTS.md — pg-service (Payment Gateway)

## Role

가맹점(merchant)과 카드사(acquirer) 사이의 결제 게이트웨이. Phase 6에서 BIN 기반 다중 acquirer 라우팅을 구현한다.

## Current Package Structure (Post Phase 5)

```
dev.pg
├── PgServiceApplication.java
├── approval/
│   ├── controller/PgApprovalController.java      ← thin HTTP adapter
│   ├── service/
│   │   ├── PgApprovalFacade.java                 ← 승인 오케스트레이터
│   │   ├── ApprovalValidationService.java        ← 입력 검증
│   │   └── PgTransactionIdGenerator.java         ← PG TX ID 생성
│   └── mapper/
│       ├── ApprovalMapper.java                   ← Transaction → Response 변환
│       └── CardAuthorizationRequestFactory.java  ← Request → CardAuth DTO 조립
├── ledger/
│   ├── entity/PaymentTransaction.java            ← 거래 원장 엔티티
│   ├── enums/ApprovalStatus.java                 ← PENDING / APPROVED / FAILED / TIMEOUT
│   ├── enums/SettlementStatus.java
│   ├── repository/PaymentTransactionRepository.java
│   └── service/
│       ├── TransactionLedgerService.java
│       └── IdempotencyService.java
├── routing/
│   ├── model/
│   │   ├── AcquirerType.java                     ← ★ Phase 6에서 확장
│   │   └── RoutingTarget.java
│   ├── policy/
│   │   ├── RoutingPolicy.java                    ← interface
│   │   └── SingleAcquirerRoutingPolicy.java      ← ★ Phase 6에서 교체
│   └── service/
│       └── AcquirerRoutingService.java           ← ★ Phase 6에서 확장
├── client/
│   ├── CardAuthorizationClient.java              ← CB + retry + error translation
│   ├── CardAuthorizationServiceClient.java       ← @FeignClient(name="card-authorization-service")
│   └── support/
│       ├── CardAuthorizationErrorType.java
│       ├── CardAuthorizationClientException.java
│       └── ExternalErrorTranslator.java
├── config/CardAuthorizationFeignConfig.java
├── dto/ (MerchantApproval*, CardAuthorization*)
└── support/
    ├── util/CardMaskingUtils.java
    └── exception/ (ErrorCode, BusinessException, GlobalExceptionHandler, ...)
```

## Phase 6: BIN 기반 다중 Acquirer 라우팅

### 목표

- 카드 번호 BIN prefix로 카드 브랜드를 식별하여 적절한 acquirer로 라우팅
- Phase 5의 RoutingPolicy 추상화를 실제로 활용
- 기존 코드 최소 변경, OCP 준수

### Routing Rule

```
Visa       (4xxx)  → Acquirer A = card-authorization-service
Mastercard (5xxx)  → Acquirer B = card-authorization-service-2
Amex       (3xxx, not 35xx) → Acquirer B
JCB        (35xx)  → Acquirer B
Discover   (6xxx)  → Acquirer B
```

### Phase 6-1. AcquirerType 확장 + CardBrand 도입

- 변경:
  - `routing/model/AcquirerType.java`에 `CARD_AUTHORIZATION_SERVICE_2` 추가
- 추가:
  - `routing/model/CardBrand.java` enum
    - VISA, MASTERCARD, AMEX, JCB, DISCOVER, UNKNOWN
    - 각 브랜드에 BIN prefix 패턴과 기본 acquirer type 매핑
- 이유:
  - 카드 브랜드 식별은 라우팅 정책의 입력, 별도 타입으로 표현해야 정책 교체가 가능
- 커밋:
  - feat(pg-service): add card brand model and extend acquirer types

### Phase 6-2. BIN 식별 서비스 도입

- 추가:
  - `routing/service/BinResolver.java`
    - `CardBrand resolve(String cardNumber)` — BIN prefix로 카드 브랜드 식별
    - longest prefix first 매칭 (`35` before `3`)
    - 매칭 실패 시 `CardBrand.UNKNOWN` 반환
- 이유:
  - BIN 식별은 라우팅 정책과 분리된 책임 (같은 BIN 식별을 fraud check 등에서도 재사용 가능)
- 검증:
  - 각 테스트 카드가 올바른 브랜드로 식별되는지 단위 테스트
- 커밋:
  - feat(pg-service): add BIN resolver for card brand identification

### Phase 6-3. BinBasedRoutingPolicy 구현

- 추가:
  - `routing/policy/BinBasedRoutingPolicy.java` implements `RoutingPolicy`
    - BinResolver를 사용하여 카드 브랜드 식별
    - 브랜드별 acquirer 매핑은 application.yaml에서 로드 (외부화)
    - UNKNOWN 브랜드: 기본 acquirer (A)로 fallback 또는 BusinessException
- 변경:
  - SingleAcquirerRoutingPolicy에 `@Primary` 제거 (또는 `@ConditionalOnProperty`로 전환)
  - BinBasedRoutingPolicy를 기본 정책으로 설정
- 이유:
  - Phase 5가 정확히 이 시점을 위해 만든 추상화. policy 교체만으로 라우팅 전략 변경
- 설정 예시 (application.yaml):
  ```yaml
  pg:
    routing:
      default-acquirer: CARD_AUTHORIZATION_SERVICE
      brand-acquirer-map:
        VISA: CARD_AUTHORIZATION_SERVICE
        MASTERCARD: CARD_AUTHORIZATION_SERVICE_2
        AMEX: CARD_AUTHORIZATION_SERVICE_2
        JCB: CARD_AUTHORIZATION_SERVICE_2
        DISCOVER: CARD_AUTHORIZATION_SERVICE_2
  ```
- 커밋:
  - feat(pg-service): implement BIN-based routing policy

### Phase 6-4. Second Acquirer FeignClient + Client 추가

- 추가:
  - `client/CardAuthorizationServiceClient2.java`
    - `@FeignClient(name = "card-authorization-service-2")`
    - 동일 API: `POST /api/authorization/request`
  - `client/CardAuthorizationClient2.java`
    - 기존 CardAuthorizationClient와 동일 패턴 (CB + retry + error translation)
    - 별도의 CircuitBreaker 인스턴스 (acquirer 별 독립 CB)
- 이유:
  - acquirer 별로 CB 상태가 독립이어야 함 (A 장애 시 B는 영향 없음)
  - FeignClient는 name 속성이 Eureka 서비스 이름과 1:1 매핑이므로 별도 인터페이스 필요
- 커밋:
  - feat(pg-service): add feign client and resilience wrapper for second acquirer

### Phase 6-5. AcquirerRoutingService 확장 — Client 선택 로직

- 변경:
  - AcquirerRoutingService가 RoutingTarget.acquirerType에 따라 올바른 Client를 선택
  - 기존: CardAuthorizationClient만 호출
  - 변경 후: acquirerType → Client 매핑 (Map 또는 switch)
- 구현 패턴 (택 1):
  - A) `Map<AcquirerType, CardAuthorizationClient>` — Spring @Qualifier로 주입
  - B) `AcquirerClientFactory` 컴포넌트 — acquirerType → client 반환
  - C) 공통 인터페이스 `AcquirerClient` 추출 후 두 Client가 구현 → Strategy 패턴
- 권장: **C안 (공통 인터페이스)**
  - `client/AcquirerClient.java` interface: `CardAuthorizationResponse authorize(CardAuthorizationRequest request)`
  - CardAuthorizationClient, CardAuthorizationClient2 모두 이 인터페이스 구현
  - AcquirerRoutingService에 `Map<AcquirerType, AcquirerClient>` 주입
- 이유:
  - 3번째 acquirer 추가 시에도 AcquirerRoutingService 코드 변경 없음 (OCP)
- 커밋:
  - refactor(pg-service): introduce acquirer client interface and dynamic client selection

### Phase 6-6. 원장에 라우팅 결과 기록

- 변경:
  - PaymentTransaction 엔티티에 `acquirerType` 필드 추가 (String 또는 enum)
  - TransactionLedgerService의 createPending에서 라우팅 결과를 함께 기록
- 이유:
  - 정산 시 "이 거래가 어느 acquirer를 통해 승인되었는가"를 알아야 함
  - 장애 분석 시 acquirer별 성공률 집계 필요
- 커밋:
  - feat(pg-service): record acquirer type in transaction ledger

### Phase 6-7. 테스트 고정

- 단위 테스트:
  - BinResolver: 7장 테스트 카드 전체의 브랜드 식별 검증, UNKNOWN 케이스
  - BinBasedRoutingPolicy: 각 브랜드가 올바른 RoutingTarget 반환
  - AcquirerRoutingService: acquirerType에 따라 올바른 client 선택
- 통합 테스트:
  - Visa 카드 → Acquirer A 도착 확인
  - Mastercard 카드 → Acquirer B 도착 확인
  - Acquirer A CB OPEN 시에도 Acquirer B는 정상 동작 (독립 CB 검증)
- E2E 테스트 (docker-compose):
  - api-gateway를 통해 Visa 카드 승인 → card-authorization-service에서 처리 확인
  - api-gateway를 통해 Amex 카드 승인 → card-authorization-service-2에서 처리 확인
- 커밋:
  - test(pg-service): cover BIN routing, client selection, and independent circuit breakers

## Upstream API (변경 없음)

```
POST /api/pg/approve
Request:  MerchantApprovalRequest { merchantTransactionId, merchantId, cardNumber, expiryDate, amount(BigDecimal), currency, mti, processingCode, transmissionDateTime, stan }
Response: MerchantApprovalResponse { merchantTransactionId, pgTransactionId, approved, responseCode, message, approvalNumber, approvedAt }
```

## Downstream API (두 acquirer 동일)

```
POST /api/authorization/request
Request:  CardAuthorizationRequest { transactionId, cardNumber, amount(BigDecimal), merchantId, terminalId, pin }
Response: CardAuthorizationResponse { transactionId, approvalNumber, responseCode, message, amount, authorizationDate, approved }
```

## Design Principles

1. **OCP 준수**: 새 acquirer 추가 시 AcquirerType enum + AcquirerClient 구현 + yaml 설정만 추가, 기존 코드 수정 최소화
2. **독립 Circuit Breaker**: acquirer별로 CB 인스턴스가 분리되어야 함. A 장애가 B에 전파되면 안 됨
3. **BIN 식별과 라우팅 정책 분리**: BinResolver(식별)와 RoutingPolicy(결정)는 별도 컴포넌트. 동일 BIN 식별을 fraud check 등에서 재사용 가능
4. **라우팅 설정 외부화**: 브랜드→acquirer 매핑은 application.yaml에서 관리. 코드 재배포 없이 라우팅 변경 가능
5. **원장 추적성**: 모든 거래에 어느 acquirer를 경유했는지 기록. 정산/분석의 전제 조건
