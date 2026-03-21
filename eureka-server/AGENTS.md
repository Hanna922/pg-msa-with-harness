# AGENTS.md — eureka-server

## Role

Netflix Eureka 기반 **서비스 디스커버리 서버**. 인프라 컴포넌트로 수정하지 않는다.

- Port: 8761 / Spring Boot 4.0.3 / Spring Cloud 2025.1.0
- 모든 서비스가 여기에 등록, FeignClient가 서비스 이름으로 인스턴스 조회
