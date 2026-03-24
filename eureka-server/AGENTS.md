# AGENTS.md - eureka-server

## Role

서비스 디스커버리 서버.

## Current Goal

`payment-service`, `ledger-service`, `settlement-service`가 정상 등록되도록 유지한다.

## Allowed Changes

- 신규 서비스 등록을 위한 최소 설정 보정
- 문서 정리

## Avoid

- 디스커버리 구조 변경
- 다른 서비스의 비즈니스 로직 흡수
- 불필요한 의존성 추가

## Expected Registered Services

- `api-gateway`
- `merchant-service`
- `payment-service`
- `ledger-service`
- `settlement-service`
- `card-authorization-service`
- `card-authorization-service-2`
- `bank-service`
