# AGENTS.md - payment-service transition module

## Role

현재 디렉터리 이름은 `pg-service`일 수 있으나, 이번 단계의 목표 서비스는 `payment-service`다.
이 서비스는 실시간 결제 승인 오케스트레이터이며, 승인 처리의 source of truth 역할을 맡는다.

## Responsibilities

- 결제 승인 API 제공
- 입력 검증과 멱등성 보장
- PG 거래 ID 생성
- BIN 기반 acquirer 라우팅
- 외부 acquirer 호출
- retry / timeout / circuit breaker / 에러 표준화
- 승인 결과 저장
- ledger-service로 승인 성공/실패 거래 기록 전송
- 결제 단건/목록 조회 API 제공

## Must Not Own

- 거래 이력 조회 저장소 역할
- 정산 금액 계산
- 정산 배치 실행
- 복식부기 회계 로직

## Public API Target

- `POST /api/payments/approve`
- `GET /api/payments/{pgTransactionId}`
- `GET /api/payments`

## Downstream Dependencies

- `card-authorization-service`
- `card-authorization-service-2`
- `ledger-service`

## Data Rules

- `approvalStatus`: `PENDING`, `APPROVED`, `FAILED`, `TIMEOUT`
- `settlementStatus`: `NOT_READY`, `READY`, `SETTLED`
- 성공과 실패 거래 모두 ledger에 기록한다.
- ledger 기록 실패가 승인 응답을 막을지 여부는 별도 정책 결정 사항이다.

## Implementation Guidance

- 라우팅/승인/저장/ledger 전송 경계를 분리한다.
- acquirer 연동은 wrapper client 패턴을 유지한다.
- API rename과 내부 책임 이동을 한 커밋에 섞지 않는다.
