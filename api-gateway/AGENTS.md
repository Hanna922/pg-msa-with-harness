# AGENTS.md — api-gateway

## Role

Spring Cloud Gateway WebMVC 기반 **API 게이트웨이**. 인프라 컴포넌트로 수정하지 않는다.

- Port: 8000 / Spring Boot 4.0.3 / Spring Cloud 2025.1.0
- 라우팅: `/api/merchant/**` → `lb://merchant-service`
- 외부에서 접근 가능한 유일한 진입점 (8000, 8761만 expose)
