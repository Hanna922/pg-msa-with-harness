# AGENTS.md - merchant-service

## Role

가맹점 스텁 서비스. 결제 승인 요청을 `payment-service`로 전달하고, 결제 요청/응답 DTO를 유지한다.

## Current Goal

`pg-service` 의존을 `payment-service`로 전환한다.

## Allowed Changes

- Feign client 서비스명 변경
- 호출 경로를 `/api/payments/approve`로 수정
- 필요시 DTO/서비스 이름 정리
- 테스트와 문서의 서비스명 변경 반영

## Avoid

- merchant 도메인 기능 확장
- 정산/ledger 책임 추가
- Fake PG 기능을 실서비스 로직과 혼합

## Integration Contract

- `@FeignClient(name = "payment-service")`
- `POST /api/payments/approve`

## Notes

- 금액 타입 차이(Integer vs BigDecimal)는 여전히 주의 대상이다.
- 독립 테스트용 `FakePgController`가 있다면 실제 연동 경로와 혼동되지 않게 유지한다.
