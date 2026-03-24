# Payment / Ledger / Settlement MSA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename `pg-service` to `payment-service`, then split transaction-history and settlement responsibilities into new `ledger-service` and `settlement-service` applications using synchronous REST integration.

**Architecture:** `payment-service` remains the real-time authorization orchestrator and source of truth for approval status. `ledger-service` stores a read-optimized transaction history copy for operations and settlement input. `settlement-service` runs batch aggregation against `ledger-service` and updates ledger settlement state after successful processing.

**Tech Stack:** Java 17, Spring Boot 3.x, Spring Cloud Eureka, Spring Cloud OpenFeign, Spring Data JPA, MySQL/H2, Resilience4j, Spring Batch, Lombok, Gradle

---

## File Structure

### Existing modules to modify

- `pg-service/` -> rename to `payment-service/`
- `merchant-service/src/main/java/dev/merchant/client/PgServiceClient.java`
- `merchant-service/src/main/resources/application.yaml`
- `api-gateway/src/main/resources/application.yaml`
- `docs/README.md`
- `docs/phase6-bin-routing-test-scenarios.md`
- `docker-compose.yml` or service-specific boot/run docs if compose is introduced in this phase
- `docker/mysql/init/` if DB bootstrap needs new schemas

### New module directories to create

- `ledger-service/`
- `settlement-service/`

### New `ledger-service` areas

- `ledger-service/build.gradle`
- `ledger-service/settings.gradle`
- `ledger-service/src/main/java/dev/ledger/LedgerServiceApplication.java`
- `ledger-service/src/main/java/dev/ledger/config/`
- `ledger-service/src/main/java/dev/ledger/controller/`
- `ledger-service/src/main/java/dev/ledger/dto/`
- `ledger-service/src/main/java/dev/ledger/entity/`
- `ledger-service/src/main/java/dev/ledger/repository/`
- `ledger-service/src/main/java/dev/ledger/service/`
- `ledger-service/src/main/resources/application.yaml`
- `ledger-service/src/test/java/dev/ledger/`

### New `settlement-service` areas

- `settlement-service/build.gradle`
- `settlement-service/settings.gradle`
- `settlement-service/src/main/java/dev/settlement/SettlementServiceApplication.java`
- `settlement-service/src/main/java/dev/settlement/batch/`
- `settlement-service/src/main/java/dev/settlement/client/`
- `settlement-service/src/main/java/dev/settlement/controller/`
- `settlement-service/src/main/java/dev/settlement/dto/`
- `settlement-service/src/main/java/dev/settlement/entity/`
- `settlement-service/src/main/java/dev/settlement/repository/`
- `settlement-service/src/main/java/dev/settlement/service/`
- `settlement-service/src/main/resources/application.yaml`
- `settlement-service/src/test/java/dev/settlement/`

---

### Task 1: Rename `pg-service` to `payment-service`

**Files:**
- Modify: repository directory `pg-service/` -> `payment-service/`
- Modify: `merchant-service/src/main/java/dev/merchant/client/PgServiceClient.java`
- Modify: `merchant-service/src/main/resources/application.yaml`
- Modify: `api-gateway/src/main/resources/application.yaml`
- Modify: `payment-service/build.gradle`
- Modify: `payment-service/settings.gradle`
- Modify: `payment-service/src/main/resources/application.yaml`
- Modify: any service docs that refer to `pg-service`
- Test: `payment-service/src/test/java/**`

- [ ] **Step 1: Rename the module directory**

Run: `Rename-Item pg-service payment-service`
Expected: `payment-service/` exists and `pg-service/` no longer exists

- [ ] **Step 2: Rename Gradle project metadata**

Update `payment-service/settings.gradle`:
```groovy
rootProject.name = 'payment-service'
```

Update `payment-service/build.gradle` group/name references only if needed.

- [ ] **Step 3: Update service registration and local config**

Update `payment-service/src/main/resources/application.yaml`:
```yaml
spring:
  application:
    name: payment-service
server:
  port: 8081
```

- [ ] **Step 4: Update merchant client binding**

Update `merchant-service/src/main/java/dev/merchant/client/PgServiceClient.java`:
```java
@FeignClient(name = "payment-service")
public interface PgServiceClient {
    @PostMapping("/api/payments/approve")
    PgAuthResponse requestPaymentAuth(@RequestBody PgAuthRequest request);
}
```

- [ ] **Step 5: Update gateway routes and docs references**

Change gateway route target from `lb://pg-service` to `lb://payment-service` and update path from `/api/pg/**` to `/api/payments/**` only if the controller is renamed in the same task; otherwise keep path migration for Task 2.

- [ ] **Step 6: Run payment-service tests after rename**

Run: `./gradlew test`
Workdir: `D:\pg-msa-with-harness\payment-service`
Expected: existing tests compile or fail only on deliberate API rename breakage

- [ ] **Step 7: Commit**

```bash
git add payment-service merchant-service api-gateway docs
git commit -m "refactor: rename pg-service module to payment-service"
```

---

### Task 2: Rename packages and public API from PG naming to Payment naming

**Files:**
- Modify: `payment-service/src/main/java/dev/pg/**`
- Modify: `payment-service/src/test/java/dev/pg/**`
- Modify: `merchant-service/src/main/java/dev/merchant/client/PgAuthRequest.java`
- Modify: `merchant-service/src/main/java/dev/merchant/client/PgAuthResponse.java`
- Modify: `merchant-service/src/main/java/dev/merchant/client/PgServiceClient.java`
- Modify: `merchant-service/src/main/java/dev/merchant/service/PaymentService.java`
- Test: `payment-service/src/test/java/**`

- [ ] **Step 1: Write a focused controller regression test for the new endpoint**

Create or update a controller test to assert the endpoint is now `/api/payments/approve`.

```java
mockMvc.perform(post("/api/payments/approve")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
    .andExpect(status().isOk());
```

- [ ] **Step 2: Run the focused test to confirm failure before implementation**

Run: `./gradlew test --tests "*PgApprovalControllerTest"`
Workdir: `D:\pg-msa-with-harness\payment-service`
Expected: FAIL because path/package names still use old PG naming

- [ ] **Step 3: Rename controller, DTOs, and service entrypoints to payment terminology**

Recommended minimal renames:
- `PgApprovalController` -> `PaymentApprovalController`
- `MerchantApprovalRequest` -> keep or rename to `PaymentApprovalRequest`
- `MerchantApprovalResponse` -> keep or rename to `PaymentApprovalResponse`
- `PgApprovalFacade` -> `PaymentApprovalFacade`
- `PgTransactionIdGenerator` -> `PaymentTransactionIdGenerator`

Do not refactor routing/ledger internals beyond naming in this task.

- [ ] **Step 4: Update merchant-service DTO/client names only where needed**

It is acceptable to keep `PgAuthRequest` and `PgAuthResponse` temporarily if full rename creates noise. If kept, add a follow-up cleanup task note in code comments or docs.

- [ ] **Step 5: Run payment-service test suite**

Run: `./gradlew test`
Workdir: `D:\pg-msa-with-harness\payment-service`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add payment-service merchant-service
git commit -m "refactor(payment-service): rename packages and approval API to payment naming"
```

---

### Task 3: Scaffold `ledger-service`

**Files:**
- Create: `ledger-service/build.gradle`
- Create: `ledger-service/settings.gradle`
- Create: `ledger-service/src/main/java/dev/ledger/LedgerServiceApplication.java`
- Create: `ledger-service/src/main/resources/application.yaml`
- Create: `ledger-service/src/test/java/dev/ledger/LedgerServiceApplicationTests.java`
- Create: `ledger-service/src/test/resources/application-test.yaml`

- [ ] **Step 1: Create the failing boot test**

```java
@SpringBootTest
class LedgerServiceApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run the failing test before adding the app skeleton**

Run: `./gradlew test --tests "dev.ledger.LedgerServiceApplicationTests"`
Workdir: `D:\pg-msa-with-harness\ledger-service`
Expected: FAIL because the module does not exist yet

- [ ] **Step 3: Create minimal Gradle and Spring Boot application files**

`ledger-service/settings.gradle`
```groovy
rootProject.name = 'ledger-service'
```

`ledger-service/build.gradle`
```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'dev.ledger'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '17'

repositories { mavenCentral() }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    runtimeOnly 'com.mysql:mysql-connector-j'
    runtimeOnly 'com.h2database:h2'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

- [ ] **Step 4: Add local application config**

```yaml
server:
  port: 8082
spring:
  application:
    name: ledger-service
  datasource:
    url: ${MYSQL_URL:jdbc:h2:mem:ledger_db;MODE=MySQL}
    username: ${MYSQL_USER:sa}
    password: ${MYSQL_PASSWORD:}
  jpa:
    hibernate:
      ddl-auto: update
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
```

- [ ] **Step 5: Run the context test**

Run: `./gradlew test --tests "dev.ledger.LedgerServiceApplicationTests"`
Workdir: `D:\pg-msa-with-harness\ledger-service`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add ledger-service
git commit -m "feat(ledger-service): scaffold transaction ledger service"
```

---

### Task 4: Implement ledger transaction record create/query/update APIs

**Files:**
- Create: `ledger-service/src/main/java/dev/ledger/entity/LedgerTransactionRecord.java`
- Create: `ledger-service/src/main/java/dev/ledger/entity/ApprovalStatus.java`
- Create: `ledger-service/src/main/java/dev/ledger/entity/SettlementStatus.java`
- Create: `ledger-service/src/main/java/dev/ledger/repository/LedgerTransactionRecordRepository.java`
- Create: `ledger-service/src/main/java/dev/ledger/dto/CreateLedgerTransactionRequest.java`
- Create: `ledger-service/src/main/java/dev/ledger/dto/LedgerTransactionResponse.java`
- Create: `ledger-service/src/main/java/dev/ledger/dto/UpdateSettlementStatusRequest.java`
- Create: `ledger-service/src/main/java/dev/ledger/service/LedgerTransactionService.java`
- Create: `ledger-service/src/main/java/dev/ledger/controller/LedgerTransactionController.java`
- Create: `ledger-service/src/test/java/dev/ledger/controller/LedgerTransactionControllerTest.java`
- Create: `ledger-service/src/test/java/dev/ledger/service/LedgerTransactionServiceTest.java`

- [ ] **Step 1: Write the failing service test for idempotent create**

```java
@Test
void createOrUpdate_storesApprovedTransaction() {
    CreateLedgerTransactionRequest request = CreateLedgerTransactionRequest.builder()
        .pgTransactionId("PG20260324A1B2C3")
        .merchantTransactionId("M-001")
        .merchantId("MERCHANT-001")
        .amount(new BigDecimal("10000"))
        .currency("KRW")
        .approvalStatus(ApprovalStatus.APPROVED)
        .settlementStatus(SettlementStatus.NOT_READY)
        .build();

    LedgerTransactionResponse response = service.createOrUpdate(request);

    assertThat(response.getPgTransactionId()).isEqualTo("PG20260324A1B2C3");
    assertThat(response.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
}
```

- [ ] **Step 2: Write the failing controller test for POST/GET/PATCH**

Test these endpoints:
- `POST /api/ledger/transactions`
- `GET /api/ledger/transactions/{pgTransactionId}`
- `PATCH /api/ledger/transactions/{pgTransactionId}/settlement-status`

- [ ] **Step 3: Run the focused ledger tests to confirm failure**

Run: `./gradlew test --tests "dev.ledger.service.LedgerTransactionServiceTest" --tests "dev.ledger.controller.LedgerTransactionControllerTest"`
Workdir: `D:\pg-msa-with-harness\ledger-service`
Expected: FAIL

- [ ] **Step 4: Implement the entity and repository with uniqueness on `pgTransactionId`**

Include at minimum:
```java
@Column(nullable = false, unique = true)
private String pgTransactionId;
```

- [ ] **Step 5: Implement service logic**

Rules:
- create if record does not exist
- update mutable fields if the same transaction is resent
- allow settlement status transition only `NOT_READY -> READY -> SETTLED`
- preserve failure records too

- [ ] **Step 6: Implement controller and query filters**

Support request params:
- `merchantId`
- `approvalStatus`
- `settlementStatus`
- `approvedFrom`
- `approvedTo`

- [ ] **Step 7: Run ledger-service tests**

Run: `./gradlew test`
Workdir: `D:\pg-msa-with-harness\ledger-service`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add ledger-service
git commit -m "feat(ledger-service): add transaction record create, query, and settlement status APIs"
```

---

### Task 5: Add ledger client integration in `payment-service`

**Files:**
- Create: `payment-service/src/main/java/dev/payment/client/LedgerServiceClient.java` or keep package root aligned with actual rename
- Create: `payment-service/src/main/java/dev/payment/client/LedgerClient.java`
- Create: `payment-service/src/main/java/dev/payment/client/dto/CreateLedgerTransactionRequest.java`
- Modify: `payment-service/src/main/java/**/approval/service/*Facade.java`
- Modify: `payment-service/src/main/resources/application.yaml`
- Create: `payment-service/src/test/java/**/client/LedgerClientTest.java`
- Modify: `payment-service/src/test/java/**/approval/service/*FacadeTest.java`

- [ ] **Step 1: Write the failing facade test for ledger sync after approval**

Add assertions for both:
- approved transaction triggers ledger sync with `APPROVED`
- failed transaction triggers ledger sync with `FAILED` or `TIMEOUT`

```java
verify(ledgerClient).syncTransaction(argThat(req ->
    req.getPgTransactionId().equals("PG20260324A1B2C3") &&
    req.getApprovalStatus().name().equals("APPROVED")
));
```

- [ ] **Step 2: Run the focused payment tests to confirm failure**

Run: `./gradlew test --tests "*FacadeTest"`
Workdir: `D:\pg-msa-with-harness\payment-service`
Expected: FAIL because ledger client does not exist yet

- [ ] **Step 3: Add Feign client and wrapper client**

```java
@FeignClient(name = "ledger-service")
public interface LedgerServiceClient {
    @PostMapping("/api/ledger/transactions")
    void createTransaction(@RequestBody CreateLedgerTransactionRequest request);
}
```

Wrap it in `LedgerClient` so retry/error translation policy stays outside the facade.

- [ ] **Step 4: Map payment transaction to ledger request DTO**

Include failure transactions as well as approved transactions.

- [ ] **Step 5: Decide and implement the 1st-phase failure policy**

Recommended implementation:
- do not fail the merchant response if ledger sync fails after approval is finalized
- log the error with `pgTransactionId`
- persist a local flag or retry-needed marker if feasible in current entity without broad refactor

- [ ] **Step 6: Run payment-service tests**

Run: `./gradlew test`
Workdir: `D:\pg-msa-with-harness\payment-service`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add payment-service
git commit -m "feat(payment-service): sync approved and failed transactions to ledger-service"
```

---

### Task 6: Scaffold `settlement-service` with Spring Batch skeleton

**Files:**
- Create: `settlement-service/build.gradle`
- Create: `settlement-service/settings.gradle`
- Create: `settlement-service/src/main/java/dev/settlement/SettlementServiceApplication.java`
- Create: `settlement-service/src/main/resources/application.yaml`
- Create: `settlement-service/src/test/java/dev/settlement/SettlementServiceApplicationTests.java`
- Create: `settlement-service/src/test/resources/application-test.yaml`

- [ ] **Step 1: Write the failing context test**

```java
@SpringBootTest
class SettlementServiceApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run the failing test before scaffold creation**

Run: `./gradlew test --tests "dev.settlement.SettlementServiceApplicationTests"`
Workdir: `D:\pg-msa-with-harness\settlement-service`
Expected: FAIL because the module does not exist yet

- [ ] **Step 3: Create minimal Gradle/app config with Spring Batch**

Dependencies must include:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-batch`
- `spring-cloud-starter-openfeign`
- `spring-cloud-starter-netflix-eureka-client`

- [ ] **Step 4: Add scheduler enabled app config**

`settlement-service/src/main/resources/application.yaml`
```yaml
server:
  port: 8083
spring:
  application:
    name: settlement-service
  batch:
    jdbc:
      initialize-schema: always
settlement:
  fee-rate: 0.025
  schedule:
    cron: 0 0 2 * * *
```

- [ ] **Step 5: Run the context test**

Run: `./gradlew test --tests "dev.settlement.SettlementServiceApplicationTests"`
Workdir: `D:\pg-msa-with-harness\settlement-service`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add settlement-service
git commit -m "feat(settlement-service): scaffold settlement service with batch skeleton"
```

---

### Task 7: Implement settlement aggregation and ledger status update flow

**Files:**
- Create: `settlement-service/src/main/java/dev/settlement/client/LedgerServiceClient.java`
- Create: `settlement-service/src/main/java/dev/settlement/client/dto/LedgerTransactionResponse.java`
- Create: `settlement-service/src/main/java/dev/settlement/client/dto/UpdateSettlementStatusRequest.java`
- Create: `settlement-service/src/main/java/dev/settlement/entity/SettlementTransaction.java`
- Create: `settlement-service/src/main/java/dev/settlement/entity/SettlementStatus.java`
- Create: `settlement-service/src/main/java/dev/settlement/repository/SettlementTransactionRepository.java`
- Create: `settlement-service/src/main/java/dev/settlement/service/SettlementCalculationService.java`
- Create: `settlement-service/src/main/java/dev/settlement/service/SettlementBatchService.java`
- Create: `settlement-service/src/main/java/dev/settlement/controller/SettlementController.java`
- Create: `settlement-service/src/main/java/dev/settlement/dto/RunSettlementRequest.java`
- Create: `settlement-service/src/main/java/dev/settlement/dto/SettlementResponse.java`
- Create: `settlement-service/src/test/java/dev/settlement/service/SettlementBatchServiceTest.java`
- Create: `settlement-service/src/test/java/dev/settlement/controller/SettlementControllerTest.java`

- [ ] **Step 1: Write the failing service test for one approved ledger record**

```java
@Test
void runSettlement_createsSettlementAndUpdatesLedgerStatus() {
    LedgerTransactionResponse tx = new LedgerTransactionResponse(
        "PG20260324A1B2C3", "M-001", "MERCHANT-001",
        new BigDecimal("10000"), "KRW", "APPROVED", "NOT_READY",
        "CARD_AUTHORIZATION_SERVICE", "0000", "Approved", "AP-1", LocalDateTime.now());

    when(ledgerServiceClient.findTransactions("MERCHANT-001", "APPROVED", "NOT_READY", null, null))
        .thenReturn(List.of(tx));

    settlementBatchService.run(LocalDate.of(2026, 3, 25));

    assertThat(repository.findAll()).hasSize(1);
    verify(ledgerServiceClient).updateSettlementStatus(eq("PG20260324A1B2C3"), any());
}
```

- [ ] **Step 2: Write the failing controller test**

Cover:
- `POST /api/settlements/run`
- `GET /api/settlements`
- `GET /api/settlements/{settlementId}`

- [ ] **Step 3: Run the focused settlement tests to confirm failure**

Run: `./gradlew test --tests "dev.settlement.service.SettlementBatchServiceTest" --tests "dev.settlement.controller.SettlementControllerTest"`
Workdir: `D:\pg-msa-with-harness\settlement-service`
Expected: FAIL

- [ ] **Step 4: Implement fee calculation service**

Minimum rule:
```java
feeAmount = amount.multiply(new BigDecimal("0.025"));
netAmount = amount.subtract(feeAmount);
```

Use `BigDecimal` with explicit scale/rounding.

- [ ] **Step 5: Implement batch flow**

Rules:
- query only `APPROVED` + `NOT_READY` ledger transactions
- create one `SettlementTransaction` per ledger transaction in phase 1
- mark local settlement as `SETTLED` only after ledger update succeeds, else `FAILED`
- ensure reruns do not duplicate by unique constraint on `pgTransactionId`

- [ ] **Step 6: Implement REST controller and optional scheduled trigger**

Manual trigger is required. Scheduler can be included if isolated from tests.

- [ ] **Step 7: Run settlement-service tests**

Run: `./gradlew test`
Workdir: `D:\pg-msa-with-harness\settlement-service`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add settlement-service
git commit -m "feat(settlement-service): add settlement aggregation and ledger status update flow"
```

---

### Task 8: Wire service discovery, gateway, databases, and docs

**Files:**
- Modify: `api-gateway/src/main/resources/application.yaml`
- Modify: `merchant-service/src/main/resources/application.yaml`
- Modify: `payment-service/src/main/resources/application.yaml`
- Modify: `ledger-service/src/main/resources/application.yaml`
- Modify: `settlement-service/src/main/resources/application.yaml`
- Modify: `docker-compose.yml` if present and in scope
- Modify: `docker/mysql/init/*` if present and in scope
- Create: `docs/phase7-payment-ledger-settlement-test-scenarios.md`
- Modify: `docs/README.md`

- [ ] **Step 1: Add gateway routes for new services**

Add route targets:
```yaml
- id: payment-service
  uri: lb://payment-service
  predicates:
    - Path=/api/payments/**
- id: ledger-service
  uri: lb://ledger-service
  predicates:
    - Path=/api/ledger/**
- id: settlement-service
  uri: lb://settlement-service
  predicates:
    - Path=/api/settlements/**
```

- [ ] **Step 2: Add DB bootstrap entries if compose/bootstrap SQL is used**

Create schemas:
- `payment_db`
- `ledger_db`
- `settlement_db`

- [ ] **Step 3: Document end-to-end scenarios**

Create `docs/phase7-payment-ledger-settlement-test-scenarios.md` with scenarios:
- approved Visa payment creates payment + ledger record
- failed Mastercard payment still creates payment + ledger failure record
- settlement run consumes only approved/not-ready records
- settlement rerun is idempotent

- [ ] **Step 4: Run service-level regression tests**

Run each:
- `./gradlew test` in `payment-service`
- `./gradlew test` in `ledger-service`
- `./gradlew test` in `settlement-service`

Expected: PASS in all three modules

- [ ] **Step 5: Run a manual integration smoke sequence**

Sequence:
1. start Eureka, gateway, merchant, payment, ledger, settlement, card-auth services
2. send one approved payment request
3. verify ledger record exists
4. run settlement trigger
5. verify settlement row exists and ledger status changes to `SETTLED`

- [ ] **Step 6: Commit**

```bash
git add api-gateway merchant-service payment-service ledger-service settlement-service docs docker
git commit -m "infra: wire payment, ledger, and settlement services with gateway and docs"
```

---

### Task 9: Final verification and cleanup

**Files:**
- Modify: any broken docs, configs, or test fixtures discovered during verification
- Test: all affected service test suites

- [ ] **Step 1: Run full affected suites again**

Run:
- `./gradlew test` in `payment-service`
- `./gradlew test` in `ledger-service`
- `./gradlew test` in `settlement-service`
- `./gradlew test` in `merchant-service` if its Feign client or DTOs changed materially

Expected: PASS

- [ ] **Step 2: Verify naming consistency**

Search for stale references:
- `pg-service`
- `/api/pg/approve`
- package path `dev.pg`

Run: `rg -n "pg-service|/api/pg/approve|package dev\.pg|import dev\.pg" D:\pg-msa-with-harness`
Expected: only intentional historical docs or explicitly accepted leftovers remain

- [ ] **Step 3: Fix any remaining low-risk inconsistencies**

Examples:
- log messages still saying pg-service
- old DTO names in public API docs
- outdated route examples

- [ ] **Step 4: Commit**

```bash
git add .
git commit -m "test: finalize payment-ledger-settlement msa verification and cleanup"
```

---

## Notes for Phase 2

Do not mix these into the 1st implementation pass:

- Kafka producer/consumer introduction
- outbox pattern
- DLQ/replay handling
- double-entry accounting ledger
- payout bank transfer automation

These are valid next-phase items only after the synchronous MSA split is stable.
