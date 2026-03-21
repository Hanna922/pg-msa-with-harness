# AGENTS.md — card-authorization-service

## Role

카드사(Acquirer) 역할의 **테스트 스텁 서비스**이다. pg-service 고도화 시 하위 연동 대상으로, 이 서비스의 코드를 직접 수정하지 않는다.

## API Spec (pg-service가 호출하는 엔드포인트)

```
POST /api/authorization/request

Request: { transactionId, cardNumber, amount(BigDecimal), merchantId, terminalId, pin }
Response: { transactionId, approvalNumber, responseCode, message, amount, authorizationDate, approved }
```

## Response Codes

- `00`: 승인, `14`: 카드 정지/분실/해지, `51`: 잔액 부족
- `54`: 유효기간 만료, `55`: PIN 오류, `61`: 한도 초과, `96`: 시스템 오류

## Key Behavior

- 카드 유효성 검증 (Luhn, 상태, 유효기간, PIN)
- CREDIT → 자체 한도 차감, DEBIT → bank-service 잔액 조회 + 출금
- Spring Boot 3.2.0 / Spring Cloud 2023.0.4 / MySQL(card_authorization_db)
- Spring Security 적용 (현재 전체 permitAll)
