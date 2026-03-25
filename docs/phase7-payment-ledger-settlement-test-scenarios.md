# Phase 7 Payment / Ledger / Settlement Test Scenarios

## Scope

This document covers the synchronous REST-based Phase 7 split:

- `merchant-service` -> `payment-service`
- `payment-service` -> `ledger-service`
- `settlement-service` -> `ledger-service`

Current assumptions:

- Kafka is not used in phase 1.
- `ledger-service` stores transaction history, not double-entry journals.
- Approved and failed payment results are both recorded in `ledger-service`.
- Settlement targets only `approvalStatus=APPROVED` and `settlementStatus=NOT_READY`.
- The default settlement fee rate is 3%.

## Required Services

Start these services before running the scenarios:

1. `eureka-server`
2. `api-gateway`
3. `merchant-service`
4. `payment-service`
5. `ledger-service`
6. `settlement-service`
7. `card-authorization-service`
8. `card-authorization-service-2`
9. `bank-service`

Note:

- This repository currently does not include a root `docker-compose.yml` or `docker/mysql/init/` bootstrap set for Phase 7.
- Run services individually with their module-level commands until infra files are added.

## Gateway Routes

Expected gateway routes:

- `/api/merchant/**` -> `merchant-service`
- `/api/payments/**` -> `payment-service`
- `/api/ledger/**` -> `ledger-service`
- `/api/settlements/**` -> `settlement-service`

## Scenario 1: Approved Payment Creates Payment and Ledger Records

Request:

```http
POST http://localhost:8000/api/merchant/payments
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

Expected results:

- `payment-service` returns an approved payment response.
- `payment-service` persists the payment transaction.
- `ledger-service` stores the same transaction snapshot.
- The ledger row has:
  - `approval_status = APPROVED`
  - `settlement_status = READY`

## Scenario 2: Failed Payment Still Creates a Ledger Record

Use a card or downstream condition that returns a failed authorization.

Expected results:

- `payment-service` returns a failed or timeout response.
- `payment-service` persists the failed payment result.
- `ledger-service` stores the failed transaction snapshot as well.
- The ledger row has:
  - `approval_status = FAILED` or `TIMEOUT`
  - `settlement_status = NOT_READY`

## Scenario 3: Settlement Run Consumes Only Approved and Not-Ready Ledger Rows

Request:

```http
POST http://localhost:8000/api/settlements/run
Content-Type: application/json
```

```json
{
  "settlementDate": "2026-03-25"
}
```

Expected results:

- `settlement-service` reads only ledger rows with:
  - `approvalStatus = APPROVED`
  - `settlementStatus = NOT_READY`
- Failed and timeout ledger rows are ignored.
- Existing settlement rows with the same `pgTransactionId` are skipped.

## Scenario 4: Settlement Calculates 3 Percent Fee and Marks Ledger as Settled

For an approved ledger transaction with `amount = 10000`:

Expected settlement values:

- `feeAmount = 300.00`
- `netAmount = 9700.00`
- settlement row `status = SETTLED`

Expected ledger update:

- `PATCH /api/ledger/transactions/{pgTransactionId}/settlement-status`
- final `settlementStatus = SETTLED`

## Scenario 5: Settlement Query APIs Return Stored Results

Requests:

```http
GET http://localhost:8000/api/settlements
GET http://localhost:8000/api/settlements/{settlementId}
```

Expected results:

- List API returns created settlement rows.
- Detail API returns the requested settlement with:
  - `settlementId`
  - `pgTransactionId`
  - `merchantId`
  - `amount`
  - `feeAmount`
  - `netAmount`
  - `currency`
  - `settlementDate`
  - `status`

## Scenario 6: Settlement Rerun Is Idempotent

Run the settlement trigger twice for the same eligible ledger row.

Expected results:

- `settlement-service` does not create a duplicate settlement row.
- The second run skips the already-settled `pgTransactionId`.

## Suggested Verification Queries

Ledger check:

```sql
SELECT pg_transaction_id,
       merchant_id,
       approval_status,
       settlement_status,
       approved_at
FROM ledger_transaction_records
ORDER BY recorded_at DESC;
```

Settlement check:

```sql
SELECT settlement_id,
       pg_transaction_id,
       merchant_id,
       amount,
       fee_amount,
       net_amount,
       settlement_date,
       status
FROM settlement_transactions
ORDER BY created_at DESC;
```
