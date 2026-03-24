# AGENTS.md - bank-service

## Role

공유 은행 스텁 서비스. 카드 계좌 잔액 조회와 출금을 담당한다.

## Current Position

`payment-service` 분리 작업과 `ledger-service`, `settlement-service` 신설의 직접 수정 대상은 아니다.

## Allowed Changes

- 다른 서비스와의 호환성을 유지하기 위한 최소 수정
- 테스트/문서 보정

## Avoid

- 정산 서비스 로직을 bank-service 내부로 이동
- payment/ledger 책임을 흡수하는 변경
- API 계약 변경

## API Contract

- 계좌 조회와 출금 API는 기존 계약을 유지한다.
- 두 acquirer가 동일 bank-service를 공유한다.
