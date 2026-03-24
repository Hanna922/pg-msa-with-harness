# AGENTS.md - card-authorization-service-2

## Role

Acquirer B 스텁 서비스. Mastercard, Amex, Discover, JCB 거래를 처리한다.

## Current Position

이번 단계의 주 수정 대상은 아니다. `payment-service`가 호출하는 두 번째 카드 승인 서비스 역할만 유지한다.

## Allowed Changes

- `payment-service` 연동을 유지하기 위한 최소 호환 수정
- 테스트/문서 보정

## Avoid

- 새로운 비즈니스 로직 추가
- 정산/ledger 책임 추가
- bank-service 계약 변경

## API Contract

- `POST /api/authorization/request`
- Request: `{ transactionId, cardNumber, amount, merchantId, terminalId, pin }`
- Response: `{ transactionId, approvalNumber, responseCode, message, amount, authorizationDate, approved }`
