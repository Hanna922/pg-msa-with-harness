# Phase 6 BIN Routing Test Scenarios

## Purpose

이 문서는 `pg-service`의 BIN 기반 다중 acquirer 라우팅을 수동 검증하기 위한 요청/기대 응답 시나리오를 정리한다.

검증 대상:

- BIN 기준 acquirer 선택
- 승인/거절 응답 코드
- 원장 `acquirer_type` 기록
- 멱등성
- acquirer 장애 시 실패 처리

## Preconditions

아래 서비스가 모두 기동 중이어야 한다.

1. `eureka-server`
2. `bank-service`
3. `card-authorization-service`
4. `card-authorization-service-2`
5. `pg-service`
6. `merchant-service`
7. `api-gateway`

추가 전제:

- `pg-service`는 MySQL로 실행되어야 한다.
- `pg_db.payment_transactions` 테이블이 생성되어 있어야 한다.

## Endpoints

### 1. Merchant Entry Point

- URL: `POST http://localhost:8000/api/merchant/payments`
- Request Body:

```json
{
  "merchantId": "MERCHANT-001",
  "cardNumber": "4111111111111111",
  "expiryDate": "2712",
  "amount": 10000
}
```

### 2. PG Direct Entry Point

- URL: `POST http://localhost:8081/api/pg/approve`
- Request Body:

```json
{
  "merchantTransactionId": "M-TEST-001",
  "merchantId": "MERCHANT-001",
  "cardNumber": "4111111111111111",
  "expiryDate": "2712",
  "amount": 10000,
  "currency": "KRW",
  "mti": "0100",
  "processingCode": "000000",
  "transmissionDateTime": "20260322120000",
  "stan": "123456"
}
```

## Important Notes

- `merchant-service` 경유 호출에서는 사용자가 `merchantTransactionId`를 넣지 않는다.
- `merchant-service`는 내부에서 새 `merchantTransactionId`를 생성하므로 응답의 거래번호는 요청 본문에 없는 값이다.
- `approvedAt`, `pgTransactionId`, `approvalNumber`, `merchantTransactionId`는 동적 값이므로 예시 응답과 정확히 같을 필요는 없다.

## Scenario 1. Visa Approval Through Acquirer A

### Merchant Request

```http
POST /api/merchant/payments
Content-Type: application/json
```

```json
{
  "merchantId": "MERCHANT-001",
  "cardNumber": "4111111111111111",
  "expiryDate": "2712",
  "amount": 10000
}
```

### Expected Merchant Response

```json
{
  "merchantTransactionId": "Mxxxxxxxxxxxxxxx",
  "merchantId": "MERCHANT-001",
  "pgTransactionId": "PG20260322120000ABCDEF",
  "amount": 10000,
  "status": "APPROVED",
  "responseCode": "00",
  "message": "승인",
  "approvedAt": "2026-03-22T12:00:05",
  "approved": true
}
```

### Expected PG Direct Response

```json
{
  "merchantTransactionId": "M-TEST-001",
  "pgTransactionId": "PG20260322120000ABCDEF",
  "approved": true,
  "responseCode": "00",
  "message": "승인",
  "approvalNumber": "12345678",
  "approvedAt": "2026-03-22T12:00:05"
}
```

### Expected Ledger State

- `acquirer_type = CARD_AUTHORIZATION_SERVICE`
- `approval_status = APPROVED`
- `response_code = 00`

## Scenario 2. Mastercard Approval Through Acquirer B

### Merchant Request

```json
{
  "merchantId": "MERCHANT-001",
  "cardNumber": "5555555555554444",
  "expiryDate": "2712",
  "amount": 10000
}
```

### Expected Merchant Response

```json
{
  "merchantTransactionId": "Mxxxxxxxxxxxxxxx",
  "merchantId": "MERCHANT-001",
  "pgTransactionId": "PG20260322120100ABCDEF",
  "amount": 10000,
  "status": "APPROVED",
  "responseCode": "00",
  "message": "승인",
  "approvedAt": "2026-03-22T12:01:05",
  "approved": true
}
```

### Expected Ledger State

- `acquirer_type = CARD_AUTHORIZATION_SERVICE_2`
- `approval_status = APPROVED`
- `response_code = 00`

## Scenario 3. Amex Approval Through Acquirer B

### Merchant Request

```json
{
  "merchantId": "MERCHANT-001",
  "cardNumber": "378282246310005",
  "expiryDate": "2712",
  "amount": 10000
}
```

### Expected Response Summary

```json
{
  "status": "APPROVED",
  "responseCode": "00",
  "approved": true
}
```

### Expected Ledger State

- `acquirer_type = CARD_AUTHORIZATION_SERVICE_2`

## Scenario 4. Discover Approval Through Acquirer B

### Merchant Request

```json
{
  "merchantId": "MERCHANT-001",
  "cardNumber": "6011111111111117",
  "expiryDate": "2712",
  "amount": 10000
}
```

### Expected Response Summary

```json
{
  "status": "APPROVED",
  "responseCode": "00",
  "approved": true
}
```

### Expected Ledger State

- `acquirer_type = CARD_AUTHORIZATION_SERVICE_2`

## Scenario 5. JCB Approval Through Acquirer B

### Merchant Request

```json
{
  "merchantId": "MERCHANT-001",
  "cardNumber": "3530111333300000",
  "expiryDate": "2712",
  "amount": 10000
}
```

### Expected Response Summary

```json
{
  "status": "APPROVED",
  "responseCode": "00",
  "approved": true
}
```

### Validation Point

- `35`가 `3`보다 먼저 해석되어 JCB로 분류되어야 한다.
- `acquirer_type = CARD_AUTHORIZATION_SERVICE_2`

## Scenario 6. Expired Visa Rejection

### Merchant Request

```json
{
  "merchantId": "MERCHANT-001",
  "cardNumber": "4012888888881881",
  "expiryDate": "2412",
  "amount": 10000
}
```

### Expected Merchant Response

```json
{
  "merchantTransactionId": "Mxxxxxxxxxxxxxxx",
  "merchantId": "MERCHANT-001",
  "pgTransactionId": "PG20260322120200ABCDEF",
  "amount": 10000,
  "status": "FAILED",
  "responseCode": "54",
  "message": "유효기간 만료",
  "approvedAt": null,
  "approved": false
}
```

### Expected Ledger State

- `acquirer_type = CARD_AUTHORIZATION_SERVICE`
- `approval_status = FAILED`
- `response_code = 54`

## Scenario 7. Suspended Mastercard Rejection

### Merchant Request

```json
{
  "merchantId": "MERCHANT-001",
  "cardNumber": "5105105105105100",
  "expiryDate": "2712",
  "amount": 10000
}
```

### Expected Merchant Response

```json
{
  "merchantTransactionId": "Mxxxxxxxxxxxxxxx",
  "merchantId": "MERCHANT-001",
  "pgTransactionId": "PG20260322120300ABCDEF",
  "amount": 10000,
  "status": "FAILED",
  "responseCode": "14",
  "message": "카드 정지",
  "approvedAt": null,
  "approved": false
}
```

### Expected Ledger State

- `acquirer_type = CARD_AUTHORIZATION_SERVICE_2`
- `approval_status = FAILED`
- `response_code = 14`

## Scenario 8. Idempotency

### PG Direct Request

동일 요청을 같은 `merchantTransactionId`로 2회 보낸다.

```json
{
  "merchantTransactionId": "M-IDEMPOTENT-001",
  "merchantId": "MERCHANT-001",
  "cardNumber": "4111111111111111",
  "expiryDate": "2712",
  "amount": 10000,
  "currency": "KRW",
  "mti": "0100",
  "processingCode": "000000",
  "transmissionDateTime": "20260322120400",
  "stan": "123458"
}
```

### Expected Behavior

- 첫 번째 요청: 정상 승인 또는 거절 결과 생성
- 두 번째 요청: 같은 결과 반환
- `payment_transactions` row 수 증가 없음

### Validation SQL

```sql
SELECT COUNT(*)
FROM payment_transactions
WHERE merchant_transaction_id = 'M-IDEMPOTENT-001';
```

### Expected SQL Result

```text
1
```

## Scenario 9. Acquirer A Down

### Setup

- `card-authorization-service` 중지

### Merchant Request

```json
{
  "merchantId": "MERCHANT-001",
  "cardNumber": "4111111111111111",
  "expiryDate": "2712",
  "amount": 10000
}
```

### Expected Merchant Response

현재 `merchant-service`는 PG 호출 예외를 broad catch로 감싸므로 아래처럼 내려올 수 있다.

```json
{
  "merchantTransactionId": "Mxxxxxxxxxxxxxxx",
  "merchantId": "MERCHANT-001",
  "pgTransactionId": null,
  "amount": 10000,
  "status": "FAILED",
  "responseCode": "99",
  "message": "PG communication error",
  "approvedAt": null,
  "approved": false
}
```

### Expected PG Direct Response

```json
{
  "merchantTransactionId": null,
  "pgTransactionId": null,
  "approved": false,
  "responseCode": "96",
  "message": "PG approval processing failed",
  "approvalNumber": null,
  "approvedAt": "2026-03-22T12:05:05"
}
```

### Expected Ledger State

- `acquirer_type = CARD_AUTHORIZATION_SERVICE`
- `approval_status = TIMEOUT` or `FAILED`
- `response_code = 96`

## Scenario 10. Acquirer B Down

### Setup

- `card-authorization-service-2` 중지

### Merchant Request

```json
{
  "merchantId": "MERCHANT-001",
  "cardNumber": "5555555555554444",
  "expiryDate": "2712",
  "amount": 10000
}
```

### Expected Merchant Response

```json
{
  "merchantTransactionId": "Mxxxxxxxxxxxxxxx",
  "merchantId": "MERCHANT-001",
  "pgTransactionId": null,
  "amount": 10000,
  "status": "FAILED",
  "responseCode": "99",
  "message": "PG communication error",
  "approvedAt": null,
  "approved": false
}
```

### Expected Ledger State

- `acquirer_type = CARD_AUTHORIZATION_SERVICE_2`
- `approval_status = TIMEOUT` or `FAILED`
- `response_code = 96`

## Ledger Validation SQL

```sql
USE pg_db;

SELECT merchant_transaction_id,
       pg_transaction_id,
       acquirer_type,
       approval_status,
       response_code,
       approval_number,
       requested_at,
       responded_at
FROM payment_transactions
ORDER BY requested_at DESC;
```

## Quick Checklist

- Visa 요청은 Acquirer A로 저장되는가
- Mastercard / Amex / Discover / JCB 요청은 Acquirer B로 저장되는가
- 만료 카드가 `54`로 거절되는가
- 정지 카드가 `14`로 거절되는가
- 같은 `merchantTransactionId` 재호출 시 row 수가 늘지 않는가
- acquirer 중단 시 `96` 계열 장애로 처리되는가
