# AGENTS.md — merchant-service

## Role

가맹점 역할의 **테스트 스텁 서비스**. 코드를 수정하지 않는다.

- `POST /api/merchant/payments` → api-gateway에서 라우팅
- PgServiceClient(FeignClient) → `POST /api/pg/approve` 호출
- Payment 엔티티: READY → APPROVED / FAILED 상태 관리
- ⚠️ amount를 Integer로 전송 (pg-service는 BigDecimal로 수신)

## Tech

Spring Boot 4.0.3 / MySQL (merchant_db) / Port 7070
