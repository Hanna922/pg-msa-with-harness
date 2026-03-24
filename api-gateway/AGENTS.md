# AGENTS.md - api-gateway

## Role

외부 진입점 라우팅 서비스. 비즈니스 로직은 갖지 않고 서비스 라우팅만 담당한다.

## Current Goal

`pg-service`에서 `payment-service`로의 명칭 변경과, 신규 `ledger-service`, `settlement-service` 라우트 추가를 반영한다.

## Allowed Changes

- `application.yaml` 라우트 추가/수정
- 서비스명 변경에 따른 `lb://` 대상 수정
- 필요 최소한의 문서/설명 변경

## Avoid

- 인증/인가 구조 개편
- 필터/프리프로세서 대규모 추가
- 결제/정산 비즈니스 로직 추가

## Required Routes

- `/api/payments/**` -> `lb://payment-service`
- `/api/ledger/**` -> `lb://ledger-service`
- `/api/settlements/**` -> `lb://settlement-service`

## Notes

- 경로 변경 시 merchant-service와 함께 정합성을 확인한다.
- 기존 `/api/pg/**` 경로를 제거할지 임시 호환시킬지는 payment-service 전환 단계에 맞춰 결정한다.
