# AGENTS.md — bank-service

## Role

은행 역할의 **테스트 스텁 서비스**. 두 acquirer(A, B) 모두가 호출하며, 이 서비스의 코드를 수정하지 않는다.

## API

```
POST /api/account/balance   → 잔액 조회 (카드번호 기반 계좌 매핑)
POST /api/account/debit     → 출금 처리 (비관적 락으로 동시성 제어)
```

## Phase 6 영향

card_account_mappings 테이블에 7장 카드 전체가 매핑되어 있으므로, 어느 acquirer가 호출하든 정상 동작한다. 변경 필요 없음.

## Tech

Spring Boot 3.2.0 / MySQL (bank_db) / Port 8080 / common 모듈 의존
