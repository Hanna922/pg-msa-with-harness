# AGENTS.md — card-authorization-service-2 (Acquirer B)

## Role

Mastercard, Amex, Discover, JCB 카드를 처리하는 **두 번째 카드사(Acquirer B) 테스트 스텁 서비스**이다.
기존 card-authorization-service와 비즈니스 로직이 동일하며, BIN 기반 라우팅 검증을 위해 존재한다.

## 생성 방법

card-authorization-service를 복제하되, 다음 항목만 변경한다.

### 변경 항목

| 항목                             | 기존 (Acquirer A)           | 신규 (Acquirer B)                  |
| -------------------------------- | --------------------------- | ---------------------------------- |
| 디렉토리명                       | card-authorization-service/ | card-authorization-service-2/      |
| spring.application.name          | card-authorization-service  | **card-authorization-service-2**   |
| server.port                      | 9090                        | **9091**                           |
| datasource URL                   | card_authorization_db       | **card_authorization_db_2**        |
| Eureka 등록 이름                 | card-authorization-service  | **card-authorization-service-2**   |
| settings.gradle rootProject.name | card-authorization-service  | **card-authorization-service-2**   |
| data.sql 테스트 카드             | Visa 카드만                 | **MC, Amex, Discover, JCB 카드만** |

### data.sql (Acquirer B 전용 카드 데이터)

기존 data.sql에서 Visa(4xxx) 카드를 제거하고, 나머지만 유지한다.

```sql
-- Acquirer B: Mastercard, Amex, Discover, JCB
INSERT INTO cards (id, card_number, card_type, card_status, expiry_date, credit_limit, used_amount, pin, customer_id, created_at, version) VALUES
(2, '5555555555554444', 'DEBIT', 'ACTIVE', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-002', NOW(), 0),
(3, '378282246310005', 'DEBIT', 'ACTIVE', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-003', NOW(), 0),
(4, '6011111111111117', 'CREDIT', 'ACTIVE', '2027-12-31', 5000000.00, 0.00, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-004', NOW(), 0),
(5, '3530111333300000', 'CREDIT', 'ACTIVE', '2027-12-31', 3000000.00, 2500000.00, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-005', NOW(), 0),
(6, '5105105105105100', 'DEBIT', 'SUSPENDED', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-006', NOW(), 0);
```

### 기존 card-authorization-service data.sql 수정

Visa(4xxx) 카드만 남기고 나머지를 제거한다.

```sql
-- Acquirer A: Visa only
INSERT INTO cards (id, card_number, card_type, card_status, expiry_date, credit_limit, used_amount, pin, customer_id, created_at, version) VALUES
(1, '4111111111111111', 'DEBIT', 'ACTIVE', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-001', NOW(), 0),
(7, '4012888888881881', 'DEBIT', 'ACTIVE', '2024-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-007', NOW(), 0);
```

### docker-compose.yml 추가 항목

```yaml
card-authorization-service-2:
  build:
    context: .
    dockerfile: card-authorization-service-2/Dockerfile
  container_name: card-authorization-service-2
  environment:
    SPRING_PROFILES_ACTIVE: docker
  expose:
    - "9091"
  networks:
    - msa-net
  depends_on:
    mysql:
      condition: service_healthy
    eureka-server:
      condition: service_started
    bank-service:
      condition: service_started
```

pg-service의 depends_on에도 추가:

```yaml
pg-service:
  depends_on:
    eureka-server:
      condition: service_started
    card-authorization-service:
      condition: service_started
    card-authorization-service-2: # 추가
      condition: service_started
```

### docker/mysql/init/01-init-databases.sql 추가

```sql
CREATE DATABASE IF NOT EXISTS card_authorization_db_2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### application-docker.yml (신규)

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/card_authorization_db_2?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
    username: root
    password: 1234
    driver-class-name: com.mysql.cj.jdbc.Driver

eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
```

## API Spec (pg-service가 호출하는 엔드포인트)

card-authorization-service와 **100% 동일**하다.

```
POST /api/authorization/request
Request:  { transactionId, cardNumber, amount(BigDecimal), merchantId, terminalId, pin }
Response: { transactionId, approvalNumber, responseCode, message, amount, authorizationDate, approved }
```

## bank-service 의존성

체크카드(DEBIT) 승인 시 bank-service에 잔액 조회 + 출금을 요청한다.
bank-service의 card_account_mappings에 이 서비스의 카드들(5555..., 3782..., 6011..., 3530..., 5105...)이 이미 매핑되어 있으므로, bank-service는 수정할 필요 없다.

## 주의 사항

- 이 서비스의 Java 코드는 수정하지 않는다 (설정 파일과 data.sql만 변경)
- 패키지명은 기존과 동일하게 `com.card.payment.authorization`을 유지한다
- Spring Boot 3.2.0 / Spring Cloud 2023.0.4 버전 유지
