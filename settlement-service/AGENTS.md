# AGENTS.md - settlement-service

## Role

정산 집계와 배치 처리 서비스. `ledger-service`의 승인 완료 거래를 조회해 정산 금액, 수수료, 상태를 관리한다.

## Current Phase Goal

Phase 7 1차 구현에서는 Spring Batch 기반의 단순 정산 집계와 상태 갱신까지를 목표로 한다.

## Responsibilities

- 정산 수동 트리거 API 제공
- 정산 배치 실행
- `ledger-service`에서 정산 대상 거래 조회
- 수수료 계산과 순정산금액 산출
- `SettlementTransaction` 저장
- 성공 시 `ledger-service`의 `settlementStatus` 갱신
- 정산 단건/목록 조회 API 제공

## Must Not Own

- 카드 승인 처리
- acquirer 직접 연동
- 승인 상태의 source of truth 저장
- 거래 이력 원본 저장소 역할

## Upstream Dependency

- `ledger-service`

## Public API Target

- `POST /api/settlements/run`
- `GET /api/settlements`
- `GET /api/settlements/{settlementId}`

## Data Rules

- 정산 대상은 `approvalStatus=APPROVED` 및 `settlementStatus=NOT_READY` 거래만 사용한다.
- 1차에서는 거래 1건당 정산 1건으로 단순화한다.
- 수수료 계산은 정률로 시작하고, merchant별 정책 확장은 후속 단계로 미룬다.
- 배치는 재실행 가능해야 하며 `pgTransactionId` 기준 중복 생성 방지 정책을 둔다.

## Implementation Guidance

- Spring Batch는 단순한 reader/service 기반 시작으로 충분하다.
- 실패 사유를 저장해 재처리 가능성을 남긴다.
- payout, reserve, chargeback, 복합 정산 규칙은 이번 범위에 넣지 않는다.
