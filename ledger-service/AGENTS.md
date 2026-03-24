# AGENTS.md - ledger-service

## Role

거래 이력 전용 원장 서비스. `payment-service`가 확정한 승인 결과를 별도 저장소에 기록하고, 운영 조회와 정산 입력 데이터를 제공한다.

## Current Phase Goal

Phase 7 1차 구현에서는 복식부기 원장이 아니라 거래 이력 read model로 시작한다.

## Responsibilities

- 거래 이력 생성 API 제공
- 거래 이력 단건/목록 조회 API 제공
- 거래별 `settlementStatus` 보관
- `settlement-service`가 사용할 정산 대상 조회 제공
- 승인 성공/실패 거래 모두 저장
- `pgTransactionId` 기준 중복 방지와 idempotent upsert 처리

## Must Not Own

- 카드 승인 호출
- BIN 라우팅
- 정산 배치 실행
- 수수료 계산
- 복식부기 회계 분개

## Upstream Dependency

- `payment-service`

## Downstream Consumer

- `settlement-service`

## Public API Target

- `POST /api/ledger/transactions`
- `GET /api/ledger/transactions/{pgTransactionId}`
- `GET /api/ledger/transactions`
- `PATCH /api/ledger/transactions/{pgTransactionId}/settlement-status`

## Data Rules

- 승인 상태 예시: `PENDING`, `APPROVED`, `FAILED`, `TIMEOUT`
- 정산 상태 예시: `NOT_READY`, `READY`, `SETTLED`
- 실패 거래도 저장하되, 정산 대상은 `approvalStatus=APPROVED` 만 포함한다.
- `settlementStatus`는 허용된 상태 전이만 반영한다.

## Implementation Guidance

- DTO 계약은 이후 Kafka 이벤트 payload로 치환 가능하게 유지한다.
- 조회 필터는 `merchantId`, `approvalStatus`, `settlementStatus`, 승인 시각 범위를 우선 지원한다.
- 엔티티는 가볍게 시작하고, 회계형 ledger 확장은 2차로 미룬다.
