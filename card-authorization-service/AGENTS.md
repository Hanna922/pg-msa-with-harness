# AGENTS.md — card-authorization-service (Acquirer A)

## Role

Visa 카드를 처리하는 **Acquirer A 테스트 스텁 서비스**이다. Java 코드를 직접 수정하지 않는다.

## Phase 6 변경 사항 ⚠️

data.sql에서 Visa가 아닌 카드를 제거해야 한다 (card-authorization-service-2로 이관).

변경 전: 7장 전체 → 변경 후: Visa(4xxx) 2장만 유지

```sql
-- Acquirer A: Visa only
INSERT INTO cards (id, card_number, card_type, card_status, expiry_date, credit_limit, used_amount, pin, customer_id, created_at, version) VALUES
(1, '4111111111111111', 'DEBIT', 'ACTIVE', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-001', NOW(), 0),
(7, '4012888888881881', 'DEBIT', 'ACTIVE', '2024-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-007', NOW(), 0);
```

## API Spec

```
POST /api/authorization/request
Request:  { transactionId, cardNumber, amount(BigDecimal), merchantId, terminalId, pin }
Response: { transactionId, approvalNumber, responseCode, message, amount, authorizationDate, approved }
```

## Response Codes

`00` 승인, `14` 카드 정지/분실/해지, `51` 잔액 부족, `54` 유효기간 만료, `55` PIN 오류, `61` 한도 초과, `96` 시스템 오류

## Tech

Spring Boot 3.2.0 / Spring Cloud 2023.0.4 / MySQL (card_authorization_db) / Port 9090
