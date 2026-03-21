# AGENTS.md — merchant-service

## Role

가맹점 역할의 **테스트 스텁 서비스**이다. 외부 클라이언트 요청을 받아 pg-service에 승인 요청을 전달한다. 이 서비스의 코드를 직접 수정하지 않는다.

## API Spec

```
POST /api/merchant/payments  → 결제 요청 (api-gateway에서 라우팅)
```

## Key Behavior

- Spring Boot 4.0.3 / MySQL(merchant_db)
- Payment 엔티티: READY → APPROVED / FAILED 상태 관리
- PgServiceClient(FeignClient) → POST /api/pg/approve 호출
- FakePgController: pg-service 없이 독립 테스트용 mock 엔드포인트
- ⚠️ amount를 Integer로 전송 (pg-service는 BigDecimal로 수신)
