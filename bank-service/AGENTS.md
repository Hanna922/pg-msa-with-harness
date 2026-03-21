# AGENTS.md — bank-service

## Role

은행 역할의 **테스트 스텁 서비스**이다. card-authorization-service가 호출하며, 이 서비스의 코드를 직접 수정하지 않는다.

## API Spec

```
POST /api/account/balance   → 잔액 조회 (카드번호 기반 계좌 매핑)
POST /api/account/debit     → 출금 처리 (비관적 락으로 동시성 제어)
```

## Key Behavior

- Spring Boot 3.2.0 / MySQL(bank_db) / common 모듈 의존
- 계좌 엔티티: balance, minimumBalance, availableBalance 계산
- 비관적 락(PESSIMISTIC_WRITE)으로 동시 출금 방지
