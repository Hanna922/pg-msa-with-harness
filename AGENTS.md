# AGENTS.md - Payment / Ledger / Settlement MSA Project

## Project Overview

카드 결제 승인 시스템을 `payment-service`, `ledger-service`, `settlement-service`로 분리하는 것이 현재 목표다.

- `payment-service`: 실시간 결제 승인 오케스트레이션
- `ledger-service`: 거래 이력 전용 원장
- `settlement-service`: 정산 집계와 배치 처리

현재 저장소에는 아직 `pg-service/` 디렉터리가 남아 있을 수 있으나, 이번 단계의 목표 이름은 `payment-service`다.

## Current Phase

Phase 7 - Payment / Ledger / Settlement MSA Separation

## Scope Constraint

### Primary targets
- `pg-service` -> `payment-service` 리네이밍 및 내부 코드 정리
- 신규 `ledger-service` 생성
- 신규 `settlement-service` 생성

### Secondary allowed changes
- `merchant-service`: `payment-service` 호출 경로와 서비스명 반영
- `api-gateway`: 신규 라우트 및 서비스명 변경 반영
- `docker-compose.yml`, `docker/mysql/init/`: 신규 서비스와 DB 추가를 위한 최소 변경
- `docs/`: 설계, 계획, 테스트 시나리오 문서 갱신

### Frozen unless strictly required for compatibility
- `card-authorization-service`
- `card-authorization-service-2`
- `bank-service`
- `eureka-server`

## Target Architecture

```text
Client
  -> api-gateway (:8000)
  -> merchant-service (:7070)
  -> payment-service (:8081)
       -> card-authorization-service (:9090)
       -> card-authorization-service-2 (:9091)
       -> ledger-service (:8082)

settlement-service (:8083)
  -> ledger-service (:8082)
```

## Key Rules

- 1차 구현은 Kafka 없이 동기 REST 연동으로 진행한다.
- `ledger-service`는 복식부기 원장이 아니라 거래 이력 저장소로 시작한다.
- 승인 성공과 실패 거래 모두 ledger에 기록한다.
- 정산은 `approvalStatus=APPROVED` 거래만 대상으로 한다.
- 서비스 간 계약은 이후 Kafka로 치환 가능하게 DTO/API 경계를 분리한다.

## Public API Baseline

### merchant-service -> payment-service
- `POST /api/payments/approve`
- `GET /api/payments/{pgTransactionId}`
- `GET /api/payments`

### payment-service -> ledger-service
- `POST /api/ledger/transactions`
- `GET /api/ledger/transactions/{pgTransactionId}`
- `GET /api/ledger/transactions`
- `PATCH /api/ledger/transactions/{pgTransactionId}/settlement-status`

### settlement-service
- `POST /api/settlements/run`
- `GET /api/settlements`
- `GET /api/settlements/{settlementId}`

## Design Principles

- `payment-service`는 승인 처리의 source of truth다.
- `ledger-service`는 조회와 정산 입력을 위한 거래 이력 read model이다.
- `settlement-service`는 배치와 수수료 계산을 담당한다.
- 리네이밍 커밋과 기능 추가 커밋을 섞지 않는다.
- 테스트와 인프라 변경은 가능한 한 별도 커밋으로 분리한다.
