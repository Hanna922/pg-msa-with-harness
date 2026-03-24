# Payment / Ledger / Settlement MSA Design

**작성일:** 2026-03-24

**목표:** 기존 `pg-service`를 `payment-service`로 리네이밍하고, 결제 승인 처리, 거래 이력 보관, 정산 배치를 각각 `payment-service`, `ledger-service`, `settlement-service`로 분리한다. 1차 목표는 Kafka 없는 동기 연동 기반 MSA 분리이며, 이후 비동기 이벤트 아키텍처로 확장 가능한 경계를 만드는 것이다.

**범위:**
- `pg-service` -> `payment-service` 리네이밍 포함
- `ledger-service` 신설
- `settlement-service` 신설
- 서비스 간 통신은 우선 REST
- `ledger-service`는 복식부기 원장이 아니라 거래 이력 전용 원장으로 단순화
- `settlement-service`는 Spring Batch 기반 정산 집계/실행 담당

**비범위:**
- Kafka, Outbox, DLQ, 이벤트 재처리
- 복식부기 journal / books / accounting ledger
- chargeback, reserve, 부분취소, 부분환불
- merchant onboarding, 정산 계좌 실지급, 외부 은행 이체 자동화

---

## 1. 설계 배경

현재 `pg-service`는 결제 승인 오케스트레이션, 거래 원장, 정산 상태의 일부 책임을 함께 가지고 있다. 이 구조는 기능이 늘어날수록 다음 문제를 만든다.

- 결제 승인 API와 정산/조회 관심사가 한 서비스에 섞인다.
- 승인 흐름 변경이 원장/정산 로직과 결합된다.
- 운영 데이터 조회와 실시간 승인 처리의 부하가 한 서비스에 몰린다.
- 이후 Kafka 기반 이벤트 처리로 고도화할 때 경계를 다시 잘라야 한다.

이번 설계의 핵심은 서비스 경계를 먼저 명확히 자르는 것이다. 1차에서는 REST 기반 동기 호출로 시작하되, 이후 `payment -> ledger`, `ledger -> settlement`를 이벤트로 바꿀 수 있도록 계약을 분리한다.

---

## 2. 업계 사례에서 가져온 기준

이번 설계는 다음 공식 자료의 공통점을 기준으로 삼는다.

- Stripe는 결제를 상태 머신으로 관리하고 승인/후속 상태 변경을 분리한다.
  - https://docs.stripe.com/payments/paymentintents/lifecycle
- Square는 원장을 별도 immutable accounting service로 분리했다.
  - https://developer.squareup.com/blog/books-an-immutable-double-entry-accounting-database-service/
- Braintree는 승인과 정산을 다른 lifecycle로 명확히 분리한다.
  - https://developer.paypal.com/braintree/articles/get-started/transaction-lifecycle
  - https://developer.paypal.com/braintree/docs/reference/general/statuses/
- Adyen은 지급/정산을 별도 운영 주기와 정산 단위로 다룬다.
  - https://docs.adyen.com/account/getting-paid

이 문서에서 내린 설계 결론은 다음과 같다.

- `payment-service`는 승인 흐름의 source of truth 여야 한다.
- 원장성 데이터는 승인 서비스와 분리되어야 한다.
- 정산은 승인과 다른 주기, 다른 장애 모델, 다른 데이터 집계 규칙을 가져야 한다.
- Kafka는 유용하지만 1차 MSA 분리의 필수 조건은 아니다.

---

## 3. 목표 아키텍처

### 3.1 서비스 구성

```
Client
  -> api-gateway
  -> merchant-service
  -> payment-service
       -> card-authorization-service / card-authorization-service-2
       -> ledger-service

settlement-service
  -> ledger-service
```

### 3.2 핵심 원칙

- `payment-service`는 승인 처리만 책임진다.
- `ledger-service`는 거래 이력 보관과 조회를 책임진다.
- `settlement-service`는 정산 집계와 배치를 책임진다.
- 서비스 간 계약은 DTO/API로 분리하고 각 서비스는 자체 DB를 가진다.
- 동기 REST 기반으로 먼저 구현하되, 이후 이벤트 기반으로 치환 가능한 경계를 남긴다.

---

## 4. 서비스별 책임

### 4.1 payment-service

`payment-service`는 결제 승인 오케스트레이터다. merchant 요청을 받아 입력을 검증하고, BIN 기반 라우팅으로 적절한 acquirer를 선택하고, 승인 결과를 자체 저장소에 확정한다.

**담당 책임**
- 결제 승인 API 제공
- 입력 검증과 멱등성 보장
- PG 거래 ID 생성
- BIN 기반 acquirer 라우팅
- 외부 acquirer 호출
- retry / timeout / circuit breaker / 에러 표준화
- 승인 결과 저장
- ledger-service로 거래 기록 전송
- 운영 조회용 결제 단건/목록 API 제공

**가져야 하는 데이터의 성격**
- 실시간 결제 처리의 기준 데이터
- 승인 흐름의 상태 머신
- acquirer 라우팅 결과와 응답 메타데이터

**하지 않아야 하는 일**
- 거래 이력 조회 최적화 저장소 역할
- 정산 금액 계산
- 정산 배치 실행
- 회계 분개 생성

### 4.2 ledger-service

`ledger-service`는 1차 단계에서 복식부기 원장이 아니라 거래 이력 전용 원장이다. `payment-service`가 결제 결과를 확정하면, 해당 결과의 스냅샷을 별도 DB에 저장한다. 이 서비스는 조회 분리와 정산 입력 제공이 목적이다.

**담당 책임**
- 거래 이력 생성 API 제공
- 거래 이력 조회 API 제공
- 거래별 정산 상태 보관
- settlement-service가 사용할 거래 데이터 조회 제공
- 거래 보관 정책과 조회 필터 최적화

**가져야 하는 데이터의 성격**
- payment 결과의 읽기 최적화된 사본
- 정산 입력용 조회 모델
- 승인 상태와 정산 상태의 조합 조회 데이터

**하지 않아야 하는 일**
- 카드 승인 호출
- BIN 라우팅
- 정산 계산
- 배치 스케줄 실행

### 4.3 settlement-service

`settlement-service`는 정산 대상 거래를 수집하고, 수수료와 순지급액을 계산하며, 배치를 통해 정산 상태를 관리하는 서비스다. 실시간 승인 API와 분리된 운영 리듬을 가진다.

**담당 책임**
- 정산 배치 실행
- 정산 대상 거래 수집
- 수수료 계산
- 정산 결과 저장
- 정산 상태 관리
- 정산 조회 / 수동 트리거 API 제공
- ledger-service의 정산 상태 갱신 호출

**가져야 하는 데이터의 성격**
- 정산 실행 이력
- merchant별 정산 금액
- 배치 실행 결과와 실패 이력

**하지 않아야 하는 일**
- 카드 승인 처리
- acquirer 연동
- 결제 승인 상태의 기준 데이터 저장

---

## 5. 데이터 모델

### 5.1 payment-service

엔티티: `PaymentTransaction`

| 필드 | 설명 |
| --- | --- |
| `id` | 내부 PK |
| `pgTransactionId` | PG 거래 ID, 외부 추적 기준 |
| `merchantTransactionId` | merchant 원거래 ID |
| `merchantId` | 가맹점 ID |
| `cardNumberMasked` | 마스킹된 카드번호 |
| `amount` | 승인 금액 |
| `currency` | 통화 |
| `approvalStatus` | `PENDING`, `APPROVED`, `FAILED`, `TIMEOUT` |
| `settlementStatus` | `NOT_READY`, `READY`, `SETTLED` |
| `acquirerType` | 라우팅된 acquirer |
| `responseCode` | 승인 응답 코드 |
| `message` | 승인 메시지 |
| `approvalNumber` | 승인 번호 |
| `approvedAt` | 승인 시각 |
| `createdAt` | 생성 시각 |
| `updatedAt` | 수정 시각 |

### 5.2 ledger-service

엔티티: `LedgerTransactionRecord`

| 필드 | 설명 |
| --- | --- |
| `id` | 내부 PK |
| `pgTransactionId` | 결제 추적 키 |
| `merchantTransactionId` | merchant 원거래 ID |
| `merchantId` | 가맹점 ID |
| `amount` | 거래 금액 |
| `currency` | 통화 |
| `approvalStatus` | 승인 상태 |
| `settlementStatus` | 정산 상태 |
| `acquirerType` | 처리 acquirer |
| `responseCode` | 승인 응답 코드 |
| `message` | 승인 메시지 |
| `approvalNumber` | 승인 번호 |
| `approvedAt` | 승인 시각 |
| `recordedAt` | ledger 기록 시각 |
| `createdAt` | 생성 시각 |
| `updatedAt` | 수정 시각 |

### 5.3 settlement-service

엔티티: `SettlementTransaction`

| 필드 | 설명 |
| --- | --- |
| `id` | 내부 PK |
| `settlementId` | 정산 추적 ID |
| `pgTransactionId` | 원거래 ID |
| `merchantId` | 가맹점 ID |
| `amount` | 원거래 금액 |
| `feeAmount` | 수수료 |
| `netAmount` | 실정산 금액 |
| `currency` | 통화 |
| `settlementDate` | 정산 기준일 |
| `status` | `PENDING`, `PROCESSING`, `SETTLED`, `FAILED` |
| `failureReason` | 실패 사유 |
| `createdAt` | 생성 시각 |
| `updatedAt` | 수정 시각 |

---

## 6. API 설계

### 6.1 payment-service API

#### `POST /api/payments/approve`

결제 승인 요청을 처리한다. 기존 `pg-service` API를 계승하되 서비스명과 컨텍스트를 정리한다.

**Request**

```json
{
  "merchantTransactionId": "M-20260324-001",
  "merchantId": "MERCHANT-001",
  "cardNumber": "4111111111111111",
  "expiryDate": "1228",
  "amount": 10000,
  "currency": "KRW",
  "mti": "0100",
  "processingCode": "000000",
  "transmissionDateTime": "0324143010",
  "stan": "123456"
}
```

**Response**

```json
{
  "merchantTransactionId": "M-20260324-001",
  "pgTransactionId": "PG20260324143010A1B2C3",
  "approved": true,
  "responseCode": "0000",
  "message": "Approved",
  "approvalNumber": "AP-123456",
  "approvedAt": "2026-03-24T14:30:12"
}
```

#### `GET /api/payments/{pgTransactionId}`

결제 단건 조회

#### `GET /api/payments`

필터 기반 결제 목록 조회

지원 필터 예시:
- `merchantId`
- `merchantTransactionId`
- `approvalStatus`
- `acquirerType`
- `from`
- `to`

### 6.2 ledger-service API

#### `POST /api/ledger/transactions`

payment-service가 승인 결과 스냅샷을 기록한다.

**Request**

```json
{
  "pgTransactionId": "PG20260324143010A1B2C3",
  "merchantTransactionId": "M-20260324-001",
  "merchantId": "MERCHANT-001",
  "amount": 10000,
  "currency": "KRW",
  "approvalStatus": "APPROVED",
  "settlementStatus": "NOT_READY",
  "acquirerType": "CARD_AUTHORIZATION_SERVICE",
  "responseCode": "0000",
  "message": "Approved",
  "approvalNumber": "AP-123456",
  "approvedAt": "2026-03-24T14:30:12"
}
```

#### `GET /api/ledger/transactions/{pgTransactionId}`

거래 이력 단건 조회

#### `GET /api/ledger/transactions`

정산 대상 조회 및 운영 조회용 목록 API

지원 필터 예시:
- `merchantId`
- `approvalStatus`
- `settlementStatus`
- `approvedFrom`
- `approvedTo`

#### `PATCH /api/ledger/transactions/{pgTransactionId}/settlement-status`

settlement-service가 정산 상태를 갱신한다.

**Request**

```json
{
  "settlementStatus": "SETTLED"
}
```

### 6.3 settlement-service API

#### `POST /api/settlements/run`

수동 배치 트리거

**Request**

```json
{
  "settlementDate": "2026-03-25"
}
```

#### `GET /api/settlements`

정산 목록 조회

지원 필터 예시:
- `merchantId`
- `status`
- `settlementDate`

#### `GET /api/settlements/{settlementId}`

정산 상세 조회

---

## 7. 호출 흐름

### 7.1 승인 성공 흐름

1. `merchant-service`가 `payment-service`의 `/api/payments/approve` 호출
2. `payment-service`가 입력 검증 및 멱등성 검사
3. `payment-service`가 BIN 기반 라우팅으로 acquirer 선택
4. `payment-service`가 acquirer 승인 요청
5. 승인 성공 시 `payment-service`가 `PaymentTransaction`을 `APPROVED`로 저장
6. `payment-service`가 `ledger-service`의 `/api/ledger/transactions` 호출
7. `ledger-service`가 거래 기록 저장
8. `payment-service`가 merchant-service에 승인 응답 반환

### 7.2 승인 실패 흐름

1. 승인 호출 실패 또는 비즈니스 거절 발생
2. payment-service가 FAILED 또는 TIMEOUT 상태 저장
3. payment-service가 실패 거래도 ledger-service에 기록
4. 표준화된 응답 코드로 merchant-service에 실패 응답 반환

실패 거래를 ledger에 남기는 이유는 다음과 같다.

- 운영 추적과 고객 문의 대응에 필요하다
- 재시도, 중복시도, 이상거래 분석의 기준 데이터가 된다
- acquirer별 실패율, 응답 코드, 라우팅 품질 분석에 필요하다
- 이후 이벤트 기반 구조로 전환할 때 성공/실패 모두 동일한 계약으로 흘려보내는 편이 안정적이다

단, 정산 배치에서는 pprovalStatus=APPROVED 인 거래만 대상에 포함한다.
### 7.3 정산 배치 흐름

1. `settlement-service`가 스케줄 또는 수동 API로 배치 시작
2. `ledger-service`에서 `approvalStatus=APPROVED` 및 `settlementStatus=NOT_READY` 거래 조회
3. merchant별 수수료 계산
4. `SettlementTransaction` 생성
5. 성공 시 `ledger-service`에 정산 상태 갱신 요청
6. 정산 조회 API에서 결과 제공

---

## 8. 장애 처리 방침

### 8.1 payment-service

- acquirer 호출은 timeout / retry / circuit breaker 유지
- ledger 기록 호출 실패 시 정책을 분리해야 한다

1차 단계 권장 정책:
- 승인 자체가 성공했으면 결제 응답은 성공으로 반환
- ledger 기록 실패는 에러 로그와 재처리 대상 상태로 남김
- 운영자가 수동 재전송 또는 보정할 수 있는 API/배치 추가

이 정책을 택하는 이유는 `ledger-service` 장애가 승인 API 전체를 막지 않게 하기 위함이다. 단, 이 경우 일시적 정합성 차이가 발생할 수 있으므로 재처리 설계를 문서화해야 한다.

### 8.2 ledger-service

- `pgTransactionId` 기준 upsert 또는 중복 방지 제약을 둔다
- 같은 거래의 중복 기록을 방지한다
- settlement 상태 갱신은 허용된 상태 전이만 통과시킨다

### 8.3 settlement-service

- 배치 실패 건은 `FAILED`로 남기고 재실행 가능해야 한다
- 수수료 계산 실패, ledger API 오류, 데이터 누락은 실패 사유를 저장한다
- 배치는 idempotent 하게 설계해 재실행이 가능해야 한다

---

## 9. 기술 선택

1차 구현에서 권장하는 기술은 다음과 같다.

- Spring Boot 3.x
- Spring Cloud Netflix Eureka
- Spring Cloud OpenFeign
- Resilience4j
- Spring Data JPA
- MySQL
- Spring Batch
- Lombok

Kafka는 2차 고도화에서 다음 위치에 도입한다.

- `payment-service`의 ledger 기록 호출 -> `payment.approved` 이벤트 발행
- `settlement-service`의 정산 대상 수집 -> ledger 또는 payment 이벤트 구독

---

## 10. 커밋 분할 전략

이번 작업은 리팩터링/분리 범위가 크므로 커밋을 작게 유지한다.

1. `refactor: rename pg-service to payment-service`
2. `refactor(payment-service): rename packages and update gateway/eureka references`
3. `feat(ledger-service): scaffold transaction ledger service`
4. `feat(payment-service): add ledger client and transaction sync`
5. `feat(settlement-service): scaffold settlement service with batch skeleton`
6. `feat(settlement-service): add settlement aggregation and ledger status update`
7. `infra: wire docker, ports, databases, and service registration`
8. `test: add integration scenarios for payment-ledger-settlement flow`
9. `docs: add phase 7 msa separation scenarios`

각 커밋은 가능한 한 아래 원칙을 따른다.

- 리네이밍 커밋과 기능 추가 커밋을 섞지 않는다.
- 서비스 스캐폴딩과 비즈니스 로직 추가를 분리한다.
- 테스트 추가와 인프라 변경을 분리한다.

---

## 11. 이후 Kafka 고도화 여지

이번 설계는 Kafka를 지금 도입하지 않는다. 대신 이후 아래 치환이 가능하도록 경계를 설계한다.

- `POST /api/ledger/transactions` -> `payment.approved` 이벤트
- `GET /api/ledger/transactions?settlementStatus=NOT_READY` 기반 polling -> `settlement.ready` 이벤트 또는 소비 기반 적재
- ledger 기록 실패 수동 재처리 -> outbox + consumer retry + DLQ

즉, 이번 설계는 “동기 MSA 1차”, 이후 “비동기 이벤트 기반 2차”로 자연스럽게 이어지는 구조를 목표로 한다.

---

## 12. 오픈 이슈

- ledger 기록 실패 시 payment 응답을 성공으로 둘지 실패로 되돌릴지 정책 확정 필요
- merchant별 수수료 정책 저장 위치를 `settlement-service` 자체 DB로 둘지 별도 merchant 설정 조회로 둘지 결정 필요
- 2차 고도화에서 복식부기 원장으로 확장할 때 `ledger-service`의 현재 테이블을 read model로 유지할지 검토 필요

---

## 13. 결론

1차 단계의 최적 목표는 Kafka를 억지로 넣는 것이 아니라, 서비스 책임을 분리하는 것이다. 따라서 이번 설계는 다음을 우선한다.

- `payment-service`는 승인 처리의 기준 시스템
- `ledger-service`는 거래 이력 저장과 조회 시스템
- `settlement-service`는 정산 집계와 배치 시스템

이 구조를 먼저 안정적으로 구현한 뒤, 이후 `payment -> ledger`, `ledger -> settlement` 경계를 Kafka 이벤트로 치환하는 것이 가장 안전하고 학습 비용 대비 효과가 크다.


