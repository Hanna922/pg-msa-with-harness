# PG 결제·정산 시스템 MSA 분리 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 현재 pg-service의 모놀리식 결제 로직을 실제 PG사 아키텍처를 참고하여 결제(Payment), 원장(Ledger), 정산(Settlement) 3개 독립 마이크로서비스로 분리하고, 이벤트 기반 비동기 통신으로 연결한다.

**Architecture:** Stripe/Toss Payments의 결제 오케스트레이션 패턴, Square Books의 복식부기 원장 패턴, 우아한형제들의 Spring Batch 기반 정산 패턴을 결합한다. payment-service가 결제 오케스트레이터 역할을 하고, ledger-service가 복식부기 원장을 관리하며, settlement-service가 배치 정산을 수행한다. 서비스 간 통신은 Kafka 이벤트로 느슨하게 결합한다.

**Tech Stack:** Java 17, Spring Boot 3.x, Spring Cloud (Eureka, OpenFeign), Apache Kafka, Spring Batch, Spring Data JPA, MySQL, Resilience4j, Gradle

---

## 아키텍처 개요

### 실제 PG사 참고 아키텍처

| 참고 회사 | 적용 패턴 | 적용 서비스 |
|-----------|-----------|------------|
| **Stripe** | PaymentIntent 상태머신, 멱등성 키 | payment-service |
| **Square Books** | 복식부기 불변 원장 (3-테이블 모델) | ledger-service |
| **Toss Payments** | 도메인별 독립 서버 인프라, 리소스 중심 API | 전체 MSA 구조 |
| **우아한형제들** | Spring Batch 정산, CQRS 이벤트 구독 | settlement-service |
| **Adyen** | Stateless Edge Layer, 수평 확장 | payment-service |

### 서비스 분리 전/후

```
[현재 - pg-service 모놀리스]
pg-service
├── controller/       ← 승인 API
├── approval/         ← 승인 오케스트레이션
├── ledger/           ← 거래 원장
├── routing/          ← BIN 라우팅
├── client/           ← 매입사 통신
└── (정산 미구현)

[목표 - 3개 마이크로서비스]
payment-service (port 8081)     ← 결제 오케스트레이션 + 라우팅
├── controller/
├── orchestrator/               ← 결제 상태머신 기반 오케스트레이션
├── routing/                    ← BIN 라우팅 (기존 유지)
├── client/                     ← 매입사 통신 (기존 유지)
└── event/publisher/            ← Kafka 이벤트 발행

ledger-service (port 8082)      ← 복식부기 원장
├── controller/                 ← 원장 조회 API
├── book/                       ← 계정(Book) 관리
├── journal/                    ← 분개(Journal Entry) 기록
├── entry/                      ← 개별 차변/대변(Book Entry)
└── event/consumer/             ← 결제 이벤트 구독

settlement-service (port 8083)  ← 배치 정산
├── controller/                 ← 정산 조회/수동 트리거 API
├── batch/                      ← Spring Batch Job/Step 정의
├── reconciliation/             ← 대사(Reconciliation) 로직
├── payout/                     ← 가맹점 정산금 지급
└── event/consumer/             ← 원장 이벤트 구독
```

### 데이터 흐름

```
Merchant → API Gateway → payment-service
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
              card-auth-1         card-auth-2
              (Visa)              (MC/Amex/...)
                    │                   │
                    └─────────┬─────────┘
                              ▼
                   Kafka: "payment-events"
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
             ledger-service    (notification 등)
                    │
           Kafka: "ledger-events"
                    │
                    ▼
           settlement-service
                    │
              Spring Batch
           (일별 정산 배치)
                    │
                    ▼
             bank-service
           (가맹점 정산금 이체)
```

### 결제 상태머신 (Stripe PaymentIntent 참고)

```
CREATED → PENDING_AUTH → AUTHORIZED → CAPTURED → SETTLED → PAID_OUT
                │              │           │
                ▼              ▼           ▼
             FAILED         VOIDED    REFUND_PENDING → REFUNDED
                                          │
                                       PARTIALLY_REFUNDED
```

### 복식부기 원장 모델 (Square Books 참고)

```
[books 테이블]          [journal_entries 테이블]      [book_entries 테이블]
id                     id                           id
name                   description                  journal_entry_id (FK)
account_type           payment_transaction_id       book_id (FK)
balance                event_type                   amount (양수=차변, 음수=대변)
currency               created_at                   created_at
merchant_id

예시: 10,000원 결제 승인
Journal Entry: "Payment AUTH PG-20260324-abc123"
  Book Entry 1: DR 고객미수금(Customer Receivable)  +10,000
  Book Entry 2: CR 가맹점미지급금(Merchant Payable)  -10,000

예시: 정산 (수수료 2.5%)
Journal Entry: "Settlement SETTLE-20260324-001"
  Book Entry 1: DR 정산계정(Settlement Account)     +10,000
  Book Entry 2: CR 가맹점지급금(Merchant Payout)      -9,750
  Book Entry 3: CR 수수료수익(Fee Revenue)             -250
```

---

## Phase 1: Kafka 인프라 및 이벤트 기반 통신 구축

### Task 1: Kafka 인프라 설정

**Files:**
- Create: `docker-compose.yml`
- Modify: `pg-service/build.gradle`

- [ ] **Step 1: docker-compose.yml 생성**

```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

- [ ] **Step 2: docker-compose 기동 확인**

Run: `docker-compose up -d`
Expected: zookeeper, kafka 컨테이너 정상 기동

- [ ] **Step 3: pg-service에 Kafka 의존성 추가**

`pg-service/build.gradle`에 추가:
```groovy
implementation 'org.springframework.kafka:spring-kafka'
```

- [ ] **Step 4: 빌드 확인**

Run: `cd pg-service && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml pg-service/build.gradle
git commit -m "infra: add Kafka docker-compose and spring-kafka dependency"
```

---

### Task 2: 결제 이벤트 모델 정의 (common 모듈)

**Files:**
- Create: `common/src/main/java/dev/common/event/PaymentEvent.java`
- Create: `common/src/main/java/dev/common/event/PaymentEventType.java`
- Create: `common/src/main/java/dev/common/event/LedgerEvent.java`
- Create: `common/src/main/java/dev/common/event/LedgerEventType.java`

- [ ] **Step 1: 결제 이벤트 타입 정의**

```java
// common/src/main/java/dev/common/event/PaymentEventType.java
package dev.common.event;

public enum PaymentEventType {
    AUTHORIZED,
    CAPTURED,
    FAILED,
    VOIDED,
    REFUND_REQUESTED
}
```

- [ ] **Step 2: 결제 이벤트 DTO 정의**

```java
// common/src/main/java/dev/common/event/PaymentEvent.java
package dev.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentEvent(
    String eventId,
    PaymentEventType eventType,
    String pgTransactionId,
    String merchantTransactionId,
    String merchantId,
    String maskedCardNumber,
    BigDecimal amount,
    String currency,
    String acquirerType,
    String approvalNumber,
    String responseCode,
    LocalDateTime occurredAt
) {}
```

- [ ] **Step 3: 원장 이벤트 타입 정의**

```java
// common/src/main/java/dev/common/event/LedgerEventType.java
package dev.common.event;

public enum LedgerEventType {
    JOURNAL_ENTRY_CREATED,
    SETTLEMENT_READY
}
```

- [ ] **Step 4: 원장 이벤트 DTO 정의**

```java
// common/src/main/java/dev/common/event/LedgerEvent.java
package dev.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LedgerEvent(
    String eventId,
    LedgerEventType eventType,
    String pgTransactionId,
    String merchantId,
    String acquirerType,
    BigDecimal amount,
    String currency,
    BigDecimal feeAmount,
    BigDecimal netAmount,
    LocalDateTime occurredAt
) {}
```

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/common/event/
git commit -m "feat(common): define PaymentEvent and LedgerEvent models"
```

---

### Task 3: pg-service에서 Kafka 이벤트 발행

**Files:**
- Create: `pg-service/src/main/java/dev/pg/event/publisher/PaymentEventPublisher.java`
- Create: `pg-service/src/main/java/dev/pg/event/config/KafkaProducerConfig.java`
- Modify: `pg-service/src/main/java/dev/pg/approval/service/PgApprovalFacade.java`
- Modify: `pg-service/src/main/resources/application.yaml`
- Create: `pg-service/src/test/java/dev/pg/event/publisher/PaymentEventPublisherTest.java`

- [ ] **Step 1: 테스트 작성 - PaymentEventPublisher**

```java
// pg-service/src/test/java/dev/pg/event/publisher/PaymentEventPublisherTest.java
package dev.pg.event.publisher;

import dev.common.event.PaymentEvent;
import dev.common.event.PaymentEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherTest {

    @Mock
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @InjectMocks
    private PaymentEventPublisher publisher;

    @Test
    void publish_sendsEventToPaymentEventsTopic() {
        PaymentEvent event = new PaymentEvent(
            "evt-001", PaymentEventType.AUTHORIZED,
            "PG-TX-001", "M-TX-001", "MERCHANT-001",
            "411111******1111", BigDecimal.valueOf(10000), "KRW",
            "CARD_AUTHORIZATION_SERVICE", "AP-001", "00",
            LocalDateTime.now()
        );

        publisher.publish(event);

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(kafkaTemplate).send(eq("payment-events"), eq("PG-TX-001"), captor.capture());
        assertThat(captor.getValue().pgTransactionId()).isEqualTo("PG-TX-001");
    }
}
```

- [ ] **Step 2: 테스트 실행 - 실패 확인**

Run: `cd pg-service && ./gradlew test --tests "dev.pg.event.publisher.PaymentEventPublisherTest"`
Expected: FAIL (PaymentEventPublisher 클래스 없음)

- [ ] **Step 3: KafkaProducerConfig 구현**

```java
// pg-service/src/main/java/dev/pg/event/config/KafkaProducerConfig.java
package dev.pg.event.config;

import dev.common.event.PaymentEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, PaymentEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, PaymentEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

- [ ] **Step 4: PaymentEventPublisher 구현**

```java
// pg-service/src/main/java/dev/pg/event/publisher/PaymentEventPublisher.java
package dev.pg.event.publisher;

import dev.common.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private static final String TOPIC = "payment-events";

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public void publish(PaymentEvent event) {
        log.info("Publishing payment event: type={}, pgTxId={}",
                event.eventType(), event.pgTransactionId());
        kafkaTemplate.send(TOPIC, event.pgTransactionId(), event);
    }
}
```

- [ ] **Step 5: application.yaml에 Kafka 설정 추가**

`pg-service/src/main/resources/application.yaml`에 추가:
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

- [ ] **Step 6: PgApprovalFacade에 이벤트 발행 통합**

`pg-service/src/main/java/dev/pg/approval/service/PgApprovalFacade.java` 수정:
- PaymentEventPublisher 주입
- 승인 성공 시 `PaymentEventType.AUTHORIZED` 이벤트 발행
- 승인 실패 시 `PaymentEventType.FAILED` 이벤트 발행

```java
// PgApprovalFacade.java - 기존 approve() 메서드 끝에 추가
private void publishPaymentEvent(PaymentEventType eventType,
                                  PaymentTransaction tx,
                                  CardAuthorizationResponse response) {
    PaymentEvent event = new PaymentEvent(
        UUID.randomUUID().toString(),
        eventType,
        tx.getPgTransactionId(),
        tx.getMerchantTransactionId(),
        tx.getMerchantId(),
        tx.getMaskedCardNumber(),
        tx.getAmount(),
        tx.getCurrency(),
        tx.getAcquirerType().name(),
        response != null ? response.approvalNumber() : null,
        response != null ? response.responseCode() : null,
        LocalDateTime.now()
    );
    paymentEventPublisher.publish(event);
}
```

- [ ] **Step 7: 테스트 실행 - 성공 확인**

Run: `cd pg-service && ./gradlew test --tests "dev.pg.event.publisher.PaymentEventPublisherTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add pg-service/src/main/java/dev/pg/event/ pg-service/src/test/java/dev/pg/event/ pg-service/src/main/resources/application.yaml
git commit -m "feat(pg-service): publish payment events to Kafka after authorization"
```

---

## Phase 2: 복식부기 원장 서비스 (ledger-service)

### Task 4: ledger-service 프로젝트 스캐폴딩

**Files:**
- Create: `ledger-service/build.gradle`
- Create: `ledger-service/src/main/java/dev/ledger/LedgerServiceApplication.java`
- Create: `ledger-service/src/main/resources/application.yaml`
- Modify: `settings.gradle` (루트)

- [ ] **Step 1: settings.gradle에 ledger-service 모듈 추가**

```groovy
include 'ledger-service'
```

- [ ] **Step 2: build.gradle 생성**

```groovy
// ledger-service/build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'dev.ledger'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '17'

repositories {
    mavenCentral()
}

dependencies {
    implementation project(':common')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.kafka:spring-kafka'
    runtimeOnly 'com.mysql:mysql-connector-j'
    runtimeOnly 'com.h2database:h2'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.kafka:spring-kafka-test'
}

ext {
    set('springCloudVersion', "2023.0.0")
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}
```

- [ ] **Step 3: Application 클래스 생성**

```java
// ledger-service/src/main/java/dev/ledger/LedgerServiceApplication.java
package dev.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LedgerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: application.yaml 생성**

```yaml
# ledger-service/src/main/resources/application.yaml
server:
  port: 8082

spring:
  application:
    name: ledger-service
  datasource:
    url: ${MYSQL_URL:jdbc:h2:mem:ledger_db}
    username: ${MYSQL_USER:sa}
    password: ${MYSQL_PASSWORD:}
    driver-class-name: ${DB_DRIVER:org.h2.Driver}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: ledger-service
      auto-offset-reset: earliest

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
```

- [ ] **Step 5: 빌드 확인**

Run: `cd ledger-service && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add ledger-service/ settings.gradle
git commit -m "feat(ledger-service): scaffold ledger-service Spring Boot project"
```

---

### Task 5: 복식부기 엔티티 모델 구현 (Square Books 패턴)

**Files:**
- Create: `ledger-service/src/main/java/dev/ledger/book/entity/Book.java`
- Create: `ledger-service/src/main/java/dev/ledger/book/entity/AccountType.java`
- Create: `ledger-service/src/main/java/dev/ledger/journal/entity/JournalEntry.java`
- Create: `ledger-service/src/main/java/dev/ledger/entry/entity/BookEntry.java`
- Create: `ledger-service/src/main/java/dev/ledger/book/repository/BookRepository.java`
- Create: `ledger-service/src/main/java/dev/ledger/journal/repository/JournalEntryRepository.java`
- Create: `ledger-service/src/main/java/dev/ledger/entry/repository/BookEntryRepository.java`
- Create: `ledger-service/src/test/java/dev/ledger/journal/entity/JournalEntryTest.java`

- [ ] **Step 1: 테스트 작성 - JournalEntry 복식부기 규칙**

```java
// ledger-service/src/test/java/dev/ledger/journal/entity/JournalEntryTest.java
package dev.ledger.journal.entity;

import dev.ledger.book.entity.AccountType;
import dev.ledger.book.entity.Book;
import dev.ledger.entry.entity.BookEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEntryTest {

    @Test
    void journalEntry_withBalancedEntries_isValid() {
        Book customerReceivable = Book.of("CUST-RCV-M001", AccountType.ASSET, "KRW", "M001");
        Book merchantPayable = Book.of("MERCH-PAY-M001", AccountType.LIABILITY, "KRW", "M001");

        JournalEntry journal = JournalEntry.create("Payment AUTH PG-TX-001", "PG-TX-001", "AUTHORIZED");
        journal.addEntry(BookEntry.debit(journal, customerReceivable, BigDecimal.valueOf(10000)));
        journal.addEntry(BookEntry.credit(journal, merchantPayable, BigDecimal.valueOf(10000)));

        assertThat(journal.isBalanced()).isTrue();
        assertThat(journal.getEntries()).hasSize(2);
    }

    @Test
    void journalEntry_withUnbalancedEntries_isNotValid() {
        Book customerReceivable = Book.of("CUST-RCV-M001", AccountType.ASSET, "KRW", "M001");
        Book merchantPayable = Book.of("MERCH-PAY-M001", AccountType.LIABILITY, "KRW", "M001");

        JournalEntry journal = JournalEntry.create("Unbalanced", "PG-TX-002", "AUTHORIZED");
        journal.addEntry(BookEntry.debit(journal, customerReceivable, BigDecimal.valueOf(10000)));
        journal.addEntry(BookEntry.credit(journal, merchantPayable, BigDecimal.valueOf(5000)));

        assertThat(journal.isBalanced()).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실행 - 실패 확인**

Run: `cd ledger-service && ../gradlew test --tests "dev.ledger.journal.entity.JournalEntryTest"`
Expected: FAIL (엔티티 클래스 없음)

- [ ] **Step 3: AccountType enum 구현**

```java
// ledger-service/src/main/java/dev/ledger/book/entity/AccountType.java
package dev.ledger.book.entity;

public enum AccountType {
    ASSET,          // 자산 (고객미수금, 정산계정)
    LIABILITY,      // 부채 (가맹점미지급금, 환불충당금)
    REVENUE,        // 수익 (수수료수익)
    EXPENSE         // 비용 (차지백 비용)
}
```

- [ ] **Step 4: Book 엔티티 구현**

```java
// ledger-service/src/main/java/dev/ledger/book/entity/Book.java
package dev.ledger.book.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String merchantId;

    public static Book of(String name, AccountType accountType, String currency, String merchantId) {
        Book book = new Book();
        book.name = name;
        book.accountType = accountType;
        book.balance = BigDecimal.ZERO;
        book.currency = currency;
        book.merchantId = merchantId;
        return book;
    }

    public void updateBalance(BigDecimal delta) {
        this.balance = this.balance.add(delta);
    }
}
```

- [ ] **Step 5: JournalEntry 엔티티 구현**

```java
// ledger-service/src/main/java/dev/ledger/journal/entity/JournalEntry.java
package dev.ledger.journal.entity;

import dev.ledger.entry.entity.BookEntry;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journal_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String paymentTransactionId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookEntry> entries = new ArrayList<>();

    public static JournalEntry create(String description, String paymentTransactionId, String eventType) {
        JournalEntry journal = new JournalEntry();
        journal.description = description;
        journal.paymentTransactionId = paymentTransactionId;
        journal.eventType = eventType;
        journal.createdAt = LocalDateTime.now();
        return journal;
    }

    public void addEntry(BookEntry entry) {
        this.entries.add(entry);
    }

    /**
     * 복식부기 규칙: 모든 차변(양수)과 대변(음수)의 합이 0이어야 한다.
     */
    public boolean isBalanced() {
        BigDecimal sum = entries.stream()
                .map(BookEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.compareTo(BigDecimal.ZERO) == 0;
    }
}
```

- [ ] **Step 6: BookEntry 엔티티 구현**

```java
// ledger-service/src/main/java/dev/ledger/entry/entity/BookEntry.java
package dev.ledger.entry.entity;

import dev.ledger.book.entity.Book;
import dev.ledger.journal.entity.JournalEntry;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "book_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    /** 양수 = 차변(Debit), 음수 = 대변(Credit) */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static BookEntry debit(JournalEntry journal, Book book, BigDecimal amount) {
        BookEntry entry = new BookEntry();
        entry.journalEntry = journal;
        entry.book = book;
        entry.amount = amount.abs();
        entry.createdAt = LocalDateTime.now();
        return entry;
    }

    public static BookEntry credit(JournalEntry journal, Book book, BigDecimal amount) {
        BookEntry entry = new BookEntry();
        entry.journalEntry = journal;
        entry.book = book;
        entry.amount = amount.abs().negate();
        entry.createdAt = LocalDateTime.now();
        return entry;
    }
}
```

- [ ] **Step 7: Repository 인터페이스 생성**

```java
// ledger-service/src/main/java/dev/ledger/book/repository/BookRepository.java
package dev.ledger.book.repository;

import dev.ledger.book.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findByName(String name);
    Optional<Book> findByNameAndMerchantId(String name, String merchantId);
}
```

```java
// ledger-service/src/main/java/dev/ledger/journal/repository/JournalEntryRepository.java
package dev.ledger.journal.repository;

import dev.ledger.journal.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByPaymentTransactionId(String paymentTransactionId);
}
```

```java
// ledger-service/src/main/java/dev/ledger/entry/repository/BookEntryRepository.java
package dev.ledger.entry.repository;

import dev.ledger.entry.entity.BookEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookEntryRepository extends JpaRepository<BookEntry, Long> {
    List<BookEntry> findByBookId(Long bookId);
}
```

- [ ] **Step 8: 테스트 실행 - 성공 확인**

Run: `cd ledger-service && ../gradlew test --tests "dev.ledger.journal.entity.JournalEntryTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add ledger-service/src/main/java/dev/ledger/book/ ledger-service/src/main/java/dev/ledger/journal/ ledger-service/src/main/java/dev/ledger/entry/
git commit -m "feat(ledger-service): implement double-entry bookkeeping entities (Square Books pattern)"
```

---

### Task 6: 원장 기록 서비스 (LedgerRecordingService)

**Files:**
- Create: `ledger-service/src/main/java/dev/ledger/journal/service/LedgerRecordingService.java`
- Create: `ledger-service/src/main/java/dev/ledger/book/service/BookService.java`
- Create: `ledger-service/src/test/java/dev/ledger/journal/service/LedgerRecordingServiceTest.java`

- [ ] **Step 1: 테스트 작성 - 결제 승인 시 원장 기록**

```java
// ledger-service/src/test/java/dev/ledger/journal/service/LedgerRecordingServiceTest.java
package dev.ledger.journal.service;

import dev.common.event.PaymentEvent;
import dev.common.event.PaymentEventType;
import dev.ledger.book.entity.AccountType;
import dev.ledger.book.entity.Book;
import dev.ledger.book.service.BookService;
import dev.ledger.entry.repository.BookEntryRepository;
import dev.ledger.journal.repository.JournalEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerRecordingServiceTest {

    @Mock private BookService bookService;
    @Mock private JournalEntryRepository journalEntryRepository;

    @InjectMocks
    private LedgerRecordingService ledgerRecordingService;

    @Test
    void recordAuthorization_createsBalancedJournalEntry() {
        PaymentEvent event = new PaymentEvent(
            "evt-001", PaymentEventType.AUTHORIZED,
            "PG-TX-001", "M-TX-001", "M001",
            "411111******1111", BigDecimal.valueOf(10000), "KRW",
            "CARD_AUTHORIZATION_SERVICE", "AP-001", "00",
            LocalDateTime.now()
        );
        Book custRecv = Book.of("CUST-RCV-M001", AccountType.ASSET, "KRW", "M001");
        Book merchPay = Book.of("MERCH-PAY-M001", AccountType.LIABILITY, "KRW", "M001");

        when(bookService.getOrCreateBook("CUST-RCV-M001", AccountType.ASSET, "KRW", "M001"))
            .thenReturn(custRecv);
        when(bookService.getOrCreateBook("MERCH-PAY-M001", AccountType.LIABILITY, "KRW", "M001"))
            .thenReturn(merchPay);
        when(journalEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerRecordingService.recordAuthorization(event);

        verify(journalEntryRepository).save(argThat(journal ->
            journal.isBalanced() && journal.getEntries().size() == 2
        ));
    }
}
```

- [ ] **Step 2: 테스트 실행 - 실패 확인**

Run: `cd ledger-service && ../gradlew test --tests "dev.ledger.journal.service.LedgerRecordingServiceTest"`
Expected: FAIL

- [ ] **Step 3: BookService 구현**

```java
// ledger-service/src/main/java/dev/ledger/book/service/BookService.java
package dev.ledger.book.service;

import dev.ledger.book.entity.AccountType;
import dev.ledger.book.entity.Book;
import dev.ledger.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;

    @Transactional
    public Book getOrCreateBook(String name, AccountType accountType, String currency, String merchantId) {
        return bookRepository.findByName(name)
                .orElseGet(() -> bookRepository.save(Book.of(name, accountType, currency, merchantId)));
    }
}
```

- [ ] **Step 4: LedgerRecordingService 구현**

```java
// ledger-service/src/main/java/dev/ledger/journal/service/LedgerRecordingService.java
package dev.ledger.journal.service;

import dev.common.event.PaymentEvent;
import dev.ledger.book.entity.AccountType;
import dev.ledger.book.entity.Book;
import dev.ledger.book.service.BookService;
import dev.ledger.entry.entity.BookEntry;
import dev.ledger.journal.entity.JournalEntry;
import dev.ledger.journal.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerRecordingService {

    private final BookService bookService;
    private final JournalEntryRepository journalEntryRepository;

    @Transactional
    public void recordAuthorization(PaymentEvent event) {
        String merchantId = event.merchantId();
        String currency = event.currency();

        Book customerReceivable = bookService.getOrCreateBook(
            "CUST-RCV-" + merchantId, AccountType.ASSET, currency, merchantId);
        Book merchantPayable = bookService.getOrCreateBook(
            "MERCH-PAY-" + merchantId, AccountType.LIABILITY, currency, merchantId);

        JournalEntry journal = JournalEntry.create(
            "Payment AUTH " + event.pgTransactionId(),
            event.pgTransactionId(),
            event.eventType().name()
        );
        journal.addEntry(BookEntry.debit(journal, customerReceivable, event.amount()));
        journal.addEntry(BookEntry.credit(journal, merchantPayable, event.amount()));

        if (!journal.isBalanced()) {
            throw new IllegalStateException("Journal entry is not balanced: " + event.pgTransactionId());
        }

        journalEntryRepository.save(journal);

        customerReceivable.updateBalance(event.amount());
        merchantPayable.updateBalance(event.amount().negate());

        log.info("Recorded authorization journal entry: pgTxId={}, amount={}",
                event.pgTransactionId(), event.amount());
    }
}
```

- [ ] **Step 5: 테스트 실행 - 성공 확인**

Run: `cd ledger-service && ../gradlew test --tests "dev.ledger.journal.service.LedgerRecordingServiceTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add ledger-service/src/main/java/dev/ledger/journal/service/ ledger-service/src/main/java/dev/ledger/book/service/ ledger-service/src/test/java/dev/ledger/journal/service/
git commit -m "feat(ledger-service): implement LedgerRecordingService with double-entry bookkeeping"
```

---

### Task 7: Kafka 이벤트 컨슈머 (결제 이벤트 → 원장 기록)

**Files:**
- Create: `ledger-service/src/main/java/dev/ledger/event/consumer/PaymentEventConsumer.java`
- Create: `ledger-service/src/main/java/dev/ledger/event/config/KafkaConsumerConfig.java`
- Create: `ledger-service/src/test/java/dev/ledger/event/consumer/PaymentEventConsumerTest.java`

- [ ] **Step 1: 테스트 작성 - PaymentEventConsumer**

```java
// ledger-service/src/test/java/dev/ledger/event/consumer/PaymentEventConsumerTest.java
package dev.ledger.event.consumer;

import dev.common.event.PaymentEvent;
import dev.common.event.PaymentEventType;
import dev.ledger.journal.service.LedgerRecordingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock private LedgerRecordingService ledgerRecordingService;
    @InjectMocks private PaymentEventConsumer consumer;

    @Test
    void consume_authorizedEvent_callsRecordAuthorization() {
        PaymentEvent event = new PaymentEvent(
            "evt-001", PaymentEventType.AUTHORIZED,
            "PG-TX-001", "M-TX-001", "M001",
            "411111******1111", BigDecimal.valueOf(10000), "KRW",
            "CARD_AUTHORIZATION_SERVICE", "AP-001", "00",
            LocalDateTime.now()
        );

        consumer.consume(event);

        verify(ledgerRecordingService).recordAuthorization(event);
    }

    @Test
    void consume_failedEvent_doesNotRecordAuthorization() {
        PaymentEvent event = new PaymentEvent(
            "evt-002", PaymentEventType.FAILED,
            "PG-TX-002", "M-TX-002", "M001",
            "411111******1111", BigDecimal.valueOf(10000), "KRW",
            "CARD_AUTHORIZATION_SERVICE", null, "51",
            LocalDateTime.now()
        );

        consumer.consume(event);

        verifyNoInteractions(ledgerRecordingService);
    }
}
```

- [ ] **Step 2: 테스트 실행 - 실패 확인**

Run: `cd ledger-service && ../gradlew test --tests "dev.ledger.event.consumer.PaymentEventConsumerTest"`
Expected: FAIL

- [ ] **Step 3: KafkaConsumerConfig 구현**

```java
// ledger-service/src/main/java/dev/ledger/event/config/KafkaConsumerConfig.java
package dev.ledger.event.config;

import dev.common.event.PaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, PaymentEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ledger-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "dev.common.event");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
```

- [ ] **Step 4: PaymentEventConsumer 구현**

```java
// ledger-service/src/main/java/dev/ledger/event/consumer/PaymentEventConsumer.java
package dev.ledger.event.consumer;

import dev.common.event.PaymentEvent;
import dev.common.event.PaymentEventType;
import dev.ledger.journal.service.LedgerRecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final LedgerRecordingService ledgerRecordingService;

    @KafkaListener(topics = "payment-events", groupId = "ledger-service")
    public void consume(PaymentEvent event) {
        log.info("Received payment event: type={}, pgTxId={}",
                event.eventType(), event.pgTransactionId());

        if (event.eventType() == PaymentEventType.AUTHORIZED) {
            ledgerRecordingService.recordAuthorization(event);
        }
    }
}
```

- [ ] **Step 5: 테스트 실행 - 성공 확인**

Run: `cd ledger-service && ../gradlew test --tests "dev.ledger.event.consumer.PaymentEventConsumerTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add ledger-service/src/main/java/dev/ledger/event/ ledger-service/src/test/java/dev/ledger/event/
git commit -m "feat(ledger-service): consume payment events from Kafka and record journal entries"
```

---

### Task 8: 원장 조회 API

**Files:**
- Create: `ledger-service/src/main/java/dev/ledger/controller/LedgerQueryController.java`
- Create: `ledger-service/src/main/java/dev/ledger/controller/dto/JournalEntryResponse.java`
- Create: `ledger-service/src/test/java/dev/ledger/controller/LedgerQueryControllerTest.java`

- [ ] **Step 1: 테스트 작성 - 원장 조회 API**

```java
// ledger-service/src/test/java/dev/ledger/controller/LedgerQueryControllerTest.java
package dev.ledger.controller;

import dev.ledger.journal.entity.JournalEntry;
import dev.ledger.journal.repository.JournalEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LedgerQueryController.class)
class LedgerQueryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private JournalEntryRepository journalEntryRepository;

    @Test
    void getJournalEntries_returnsEntriesForTransaction() throws Exception {
        JournalEntry journal = JournalEntry.create("Payment AUTH PG-TX-001", "PG-TX-001", "AUTHORIZED");
        when(journalEntryRepository.findByPaymentTransactionId("PG-TX-001"))
            .thenReturn(List.of(journal));

        mockMvc.perform(get("/api/ledger/journals/PG-TX-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].description").value("Payment AUTH PG-TX-001"));
    }
}
```

- [ ] **Step 2: 테스트 실행 - 실패 확인**

Run: `cd ledger-service && ../gradlew test --tests "dev.ledger.controller.LedgerQueryControllerTest"`
Expected: FAIL

- [ ] **Step 3: DTO 및 Controller 구현**

```java
// ledger-service/src/main/java/dev/ledger/controller/dto/JournalEntryResponse.java
package dev.ledger.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record JournalEntryResponse(
    Long id,
    String description,
    String paymentTransactionId,
    String eventType,
    LocalDateTime createdAt,
    List<BookEntryResponse> entries
) {
    public record BookEntryResponse(
        Long id,
        String bookName,
        BigDecimal amount,
        LocalDateTime createdAt
    ) {}
}
```

```java
// ledger-service/src/main/java/dev/ledger/controller/LedgerQueryController.java
package dev.ledger.controller;

import dev.ledger.controller.dto.JournalEntryResponse;
import dev.ledger.journal.entity.JournalEntry;
import dev.ledger.journal.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerQueryController {

    private final JournalEntryRepository journalEntryRepository;

    @GetMapping("/journals/{pgTransactionId}")
    public List<JournalEntryResponse> getJournalEntries(@PathVariable String pgTransactionId) {
        return journalEntryRepository.findByPaymentTransactionId(pgTransactionId).stream()
                .map(this::toResponse)
                .toList();
    }

    private JournalEntryResponse toResponse(JournalEntry journal) {
        return new JournalEntryResponse(
            journal.getId(),
            journal.getDescription(),
            journal.getPaymentTransactionId(),
            journal.getEventType(),
            journal.getCreatedAt(),
            journal.getEntries().stream()
                .map(entry -> new JournalEntryResponse.BookEntryResponse(
                    entry.getId(),
                    entry.getBook().getName(),
                    entry.getAmount(),
                    entry.getCreatedAt()
                ))
                .toList()
        );
    }
}
```

- [ ] **Step 4: 테스트 실행 - 성공 확인**

Run: `cd ledger-service && ../gradlew test --tests "dev.ledger.controller.LedgerQueryControllerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ledger-service/src/main/java/dev/ledger/controller/ ledger-service/src/test/java/dev/ledger/controller/
git commit -m "feat(ledger-service): add ledger query API for journal entries"
```

---

## Phase 3: 정산 서비스 (settlement-service)

### Task 9: settlement-service 프로젝트 스캐폴딩

**Files:**
- Create: `settlement-service/build.gradle`
- Create: `settlement-service/src/main/java/dev/settlement/SettlementServiceApplication.java`
- Create: `settlement-service/src/main/resources/application.yaml`
- Modify: `settings.gradle` (루트)

- [ ] **Step 1: settings.gradle에 settlement-service 모듈 추가**

```groovy
include 'settlement-service'
```

- [ ] **Step 2: build.gradle 생성**

```groovy
// settlement-service/build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'dev.settlement'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '17'

repositories {
    mavenCentral()
}

dependencies {
    implementation project(':common')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-batch'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'org.springframework.kafka:spring-kafka'
    runtimeOnly 'com.mysql:mysql-connector-j'
    runtimeOnly 'com.h2database:h2'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.batch:spring-batch-test'
    testImplementation 'org.springframework.kafka:spring-kafka-test'
}

ext {
    set('springCloudVersion', "2023.0.0")
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}
```

- [ ] **Step 3: Application 클래스 생성**

```java
// settlement-service/src/main/java/dev/settlement/SettlementServiceApplication.java
package dev.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableBatchProcessing
@EnableFeignClients
public class SettlementServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: application.yaml 생성**

```yaml
# settlement-service/src/main/resources/application.yaml
server:
  port: 8083

spring:
  application:
    name: settlement-service
  datasource:
    url: ${MYSQL_URL:jdbc:h2:mem:settlement_db}
    username: ${MYSQL_USER:sa}
    password: ${MYSQL_PASSWORD:}
    driver-class-name: ${DB_DRIVER:org.h2.Driver}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: settlement-service
      auto-offset-reset: earliest
  batch:
    jdbc:
      initialize-schema: always
    job:
      enabled: false  # 스케줄러로 수동 트리거

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}

settlement:
  fee-rate: ${SETTLEMENT_FEE_RATE:0.025}  # 2.5% MDR
  schedule:
    cron: ${SETTLEMENT_CRON:0 0 2 * * *}  # 매일 새벽 2시
```

- [ ] **Step 5: 빌드 확인**

Run: `cd settlement-service && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add settlement-service/ settings.gradle
git commit -m "feat(settlement-service): scaffold settlement-service with Spring Batch"
```

---

### Task 10: 정산 대상 엔티티 및 Kafka 이벤트 수집

**Files:**
- Create: `settlement-service/src/main/java/dev/settlement/transaction/entity/SettlementTransaction.java`
- Create: `settlement-service/src/main/java/dev/settlement/transaction/entity/SettlementTransactionStatus.java`
- Create: `settlement-service/src/main/java/dev/settlement/transaction/repository/SettlementTransactionRepository.java`
- Create: `settlement-service/src/main/java/dev/settlement/event/consumer/PaymentEventConsumer.java`
- Create: `settlement-service/src/main/java/dev/settlement/event/config/KafkaConsumerConfig.java`
- Create: `settlement-service/src/test/java/dev/settlement/event/consumer/PaymentEventConsumerTest.java`

- [ ] **Step 1: SettlementTransactionStatus enum**

```java
// settlement-service/src/main/java/dev/settlement/transaction/entity/SettlementTransactionStatus.java
package dev.settlement.transaction.entity;

public enum SettlementTransactionStatus {
    PENDING,        // 정산 대기
    PROCESSING,     // 정산 배치 처리 중
    SETTLED,        // 정산 완료
    PAID_OUT,       // 가맹점 지급 완료
    FAILED          // 정산 실패
}
```

- [ ] **Step 2: SettlementTransaction 엔티티**

```java
// settlement-service/src/main/java/dev/settlement/transaction/entity/SettlementTransaction.java
package dev.settlement.transaction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String pgTransactionId;

    @Column(nullable = false)
    private String merchantId;

    @Column(nullable = false)
    private String acquirerType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(precision = 19, scale = 2)
    private BigDecimal feeAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementTransactionStatus status;

    @Column(nullable = false)
    private LocalDate settlementDate;

    @Column(nullable = false)
    private LocalDateTime authorizedAt;

    private LocalDateTime settledAt;

    private String settlementBatchId;

    public static SettlementTransaction fromPaymentEvent(
            String pgTransactionId, String merchantId, String acquirerType,
            BigDecimal amount, String currency, LocalDateTime authorizedAt) {
        SettlementTransaction tx = new SettlementTransaction();
        tx.pgTransactionId = pgTransactionId;
        tx.merchantId = merchantId;
        tx.acquirerType = acquirerType;
        tx.amount = amount;
        tx.currency = currency;
        tx.status = SettlementTransactionStatus.PENDING;
        tx.settlementDate = authorizedAt.toLocalDate().plusDays(1); // T+1
        tx.authorizedAt = authorizedAt;
        return tx;
    }

    public void markProcessing(String batchId, BigDecimal feeRate) {
        this.status = SettlementTransactionStatus.PROCESSING;
        this.settlementBatchId = batchId;
        this.feeAmount = this.amount.multiply(feeRate);
        this.netAmount = this.amount.subtract(this.feeAmount);
    }

    public void markSettled() {
        this.status = SettlementTransactionStatus.SETTLED;
        this.settledAt = LocalDateTime.now();
    }

    public void markPaidOut() {
        this.status = SettlementTransactionStatus.PAID_OUT;
    }

    public void markFailed() {
        this.status = SettlementTransactionStatus.FAILED;
    }
}
```

- [ ] **Step 3: Repository**

```java
// settlement-service/src/main/java/dev/settlement/transaction/repository/SettlementTransactionRepository.java
package dev.settlement.transaction.repository;

import dev.settlement.transaction.entity.SettlementTransaction;
import dev.settlement.transaction.entity.SettlementTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, Long> {
    Optional<SettlementTransaction> findByPgTransactionId(String pgTransactionId);
    List<SettlementTransaction> findByStatusAndSettlementDateLessThanEqual(
            SettlementTransactionStatus status, LocalDate date);
}
```

- [ ] **Step 4: 테스트 작성 - PaymentEventConsumer**

```java
// settlement-service/src/test/java/dev/settlement/event/consumer/PaymentEventConsumerTest.java
package dev.settlement.event.consumer;

import dev.common.event.PaymentEvent;
import dev.common.event.PaymentEventType;
import dev.settlement.transaction.entity.SettlementTransaction;
import dev.settlement.transaction.entity.SettlementTransactionStatus;
import dev.settlement.transaction.repository.SettlementTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock private SettlementTransactionRepository repository;
    @InjectMocks private PaymentEventConsumer consumer;

    @Test
    void consume_authorizedEvent_createsSettlementTransaction() {
        PaymentEvent event = new PaymentEvent(
            "evt-001", PaymentEventType.AUTHORIZED,
            "PG-TX-001", "M-TX-001", "M001",
            "411111******1111", BigDecimal.valueOf(10000), "KRW",
            "CARD_AUTHORIZATION_SERVICE", "AP-001", "00",
            LocalDateTime.of(2026, 3, 24, 14, 30)
        );

        consumer.consume(event);

        ArgumentCaptor<SettlementTransaction> captor =
            ArgumentCaptor.forClass(SettlementTransaction.class);
        verify(repository).save(captor.capture());

        SettlementTransaction saved = captor.getValue();
        assertThat(saved.getPgTransactionId()).isEqualTo("PG-TX-001");
        assertThat(saved.getStatus()).isEqualTo(SettlementTransactionStatus.PENDING);
        assertThat(saved.getSettlementDate()).isEqualTo("2026-03-25"); // T+1
    }
}
```

- [ ] **Step 5: 테스트 실행 - 실패 확인**

Run: `cd settlement-service && ../gradlew test --tests "dev.settlement.event.consumer.PaymentEventConsumerTest"`
Expected: FAIL

- [ ] **Step 6: KafkaConsumerConfig 구현**

```java
// settlement-service/src/main/java/dev/settlement/event/config/KafkaConsumerConfig.java
package dev.settlement.event.config;

import dev.common.event.PaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, PaymentEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "settlement-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "dev.common.event");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
```

- [ ] **Step 7: PaymentEventConsumer 구현**

```java
// settlement-service/src/main/java/dev/settlement/event/consumer/PaymentEventConsumer.java
package dev.settlement.event.consumer;

import dev.common.event.PaymentEvent;
import dev.common.event.PaymentEventType;
import dev.settlement.transaction.entity.SettlementTransaction;
import dev.settlement.transaction.repository.SettlementTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final SettlementTransactionRepository repository;

    @KafkaListener(topics = "payment-events", groupId = "settlement-service")
    public void consume(PaymentEvent event) {
        log.info("Received payment event: type={}, pgTxId={}",
                event.eventType(), event.pgTransactionId());

        if (event.eventType() == PaymentEventType.AUTHORIZED) {
            SettlementTransaction tx = SettlementTransaction.fromPaymentEvent(
                event.pgTransactionId(),
                event.merchantId(),
                event.acquirerType(),
                event.amount(),
                event.currency(),
                event.occurredAt()
            );
            repository.save(tx);
            log.info("Created settlement transaction: pgTxId={}, settlementDate={}",
                    tx.getPgTransactionId(), tx.getSettlementDate());
        }
    }
}
```

- [ ] **Step 8: 테스트 실행 - 성공 확인**

Run: `cd settlement-service && ../gradlew test --tests "dev.settlement.event.consumer.PaymentEventConsumerTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add settlement-service/src/main/java/dev/settlement/transaction/ settlement-service/src/main/java/dev/settlement/event/ settlement-service/src/test/java/dev/settlement/event/
git commit -m "feat(settlement-service): consume payment events and create settlement transactions (T+1)"
```

---

### Task 11: Spring Batch 일별 정산 배치 Job (우아한형제들 패턴)

**Files:**
- Create: `settlement-service/src/main/java/dev/settlement/batch/config/DailySettlementJobConfig.java`
- Create: `settlement-service/src/main/java/dev/settlement/batch/processor/SettlementFeeProcessor.java`
- Create: `settlement-service/src/main/java/dev/settlement/batch/entity/SettlementBatch.java`
- Create: `settlement-service/src/main/java/dev/settlement/batch/repository/SettlementBatchRepository.java`
- Create: `settlement-service/src/main/java/dev/settlement/batch/scheduler/SettlementScheduler.java`
- Create: `settlement-service/src/test/java/dev/settlement/batch/processor/SettlementFeeProcessorTest.java`

- [ ] **Step 1: 테스트 작성 - SettlementFeeProcessor**

```java
// settlement-service/src/test/java/dev/settlement/batch/processor/SettlementFeeProcessorTest.java
package dev.settlement.batch.processor;

import dev.settlement.transaction.entity.SettlementTransaction;
import dev.settlement.transaction.entity.SettlementTransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementFeeProcessorTest {

    private final SettlementFeeProcessor processor =
        new SettlementFeeProcessor(new BigDecimal("0.025")); // 2.5% MDR

    @Test
    void process_calculatesFeeAndNetAmount() throws Exception {
        SettlementTransaction tx = SettlementTransaction.fromPaymentEvent(
            "PG-TX-001", "M001", "CARD_AUTHORIZATION_SERVICE",
            BigDecimal.valueOf(10000), "KRW",
            LocalDateTime.of(2026, 3, 24, 14, 30)
        );

        SettlementTransaction result = processor.process(tx);

        assertThat(result.getStatus()).isEqualTo(SettlementTransactionStatus.PROCESSING);
        assertThat(result.getFeeAmount()).isEqualByComparingTo("250.00");
        assertThat(result.getNetAmount()).isEqualByComparingTo("9750.00");
    }
}
```

- [ ] **Step 2: 테스트 실행 - 실패 확인**

Run: `cd settlement-service && ../gradlew test --tests "dev.settlement.batch.processor.SettlementFeeProcessorTest"`
Expected: FAIL

- [ ] **Step 3: SettlementBatch 엔티티**

```java
// settlement-service/src/main/java/dev/settlement/batch/entity/SettlementBatch.java
package dev.settlement.batch.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String batchId;

    @Column(nullable = false)
    private LocalDate settlementDate;

    private int totalTransactions;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalFee;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalNetAmount;

    @Column(nullable = false)
    private String status; // PROCESSING, COMPLETED, FAILED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    public static SettlementBatch create(String batchId, LocalDate settlementDate) {
        SettlementBatch batch = new SettlementBatch();
        batch.batchId = batchId;
        batch.settlementDate = settlementDate;
        batch.totalTransactions = 0;
        batch.totalAmount = BigDecimal.ZERO;
        batch.totalFee = BigDecimal.ZERO;
        batch.totalNetAmount = BigDecimal.ZERO;
        batch.status = "PROCESSING";
        batch.createdAt = LocalDateTime.now();
        return batch;
    }

    public void addTransaction(BigDecimal amount, BigDecimal fee, BigDecimal netAmount) {
        this.totalTransactions++;
        this.totalAmount = this.totalAmount.add(amount);
        this.totalFee = this.totalFee.add(fee);
        this.totalNetAmount = this.totalNetAmount.add(netAmount);
    }

    public void complete() {
        this.status = "COMPLETED";
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = "FAILED";
        this.completedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 4: SettlementBatchRepository**

```java
// settlement-service/src/main/java/dev/settlement/batch/repository/SettlementBatchRepository.java
package dev.settlement.batch.repository;

import dev.settlement.batch.entity.SettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {
}
```

- [ ] **Step 5: SettlementFeeProcessor 구현**

```java
// settlement-service/src/main/java/dev/settlement/batch/processor/SettlementFeeProcessor.java
package dev.settlement.batch.processor;

import dev.settlement.transaction.entity.SettlementTransaction;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;
import java.util.UUID;

public class SettlementFeeProcessor implements ItemProcessor<SettlementTransaction, SettlementTransaction> {

    private final BigDecimal feeRate;

    public SettlementFeeProcessor(BigDecimal feeRate) {
        this.feeRate = feeRate;
    }

    @Override
    public SettlementTransaction process(SettlementTransaction item) {
        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8);
        item.markProcessing(batchId, feeRate);
        return item;
    }
}
```

- [ ] **Step 6: DailySettlementJobConfig 구현**

```java
// settlement-service/src/main/java/dev/settlement/batch/config/DailySettlementJobConfig.java
package dev.settlement.batch.config;

import dev.settlement.batch.processor.SettlementFeeProcessor;
import dev.settlement.transaction.entity.SettlementTransaction;
import dev.settlement.transaction.entity.SettlementTransactionStatus;
import dev.settlement.transaction.repository.SettlementTransactionRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
@RequiredArgsConstructor
public class DailySettlementJobConfig {

    private final SettlementTransactionRepository settlementTransactionRepository;

    @Value("${settlement.fee-rate:0.025}")
    private BigDecimal feeRate;

    @Bean
    public Job dailySettlementJob(JobRepository jobRepository, Step settlementStep) {
        return new JobBuilder("dailySettlementJob", jobRepository)
                .start(settlementStep)
                .build();
    }

    @Bean
    public Step settlementStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager) {
        return new StepBuilder("settlementStep", jobRepository)
                .<SettlementTransaction, SettlementTransaction>chunk(100, transactionManager)
                .reader(pendingTransactionReader())
                .processor(settlementFeeProcessor())
                .writer(settlementWriter())
                .build();
    }

    @Bean
    public ItemReader<SettlementTransaction> pendingTransactionReader() {
        return new ListItemReader<>(
            settlementTransactionRepository.findByStatusAndSettlementDateLessThanEqual(
                SettlementTransactionStatus.PENDING, LocalDate.now()
            )
        );
    }

    @Bean
    public SettlementFeeProcessor settlementFeeProcessor() {
        return new SettlementFeeProcessor(feeRate);
    }

    @Bean
    public ItemWriter<SettlementTransaction> settlementWriter() {
        return items -> {
            for (SettlementTransaction tx : items) {
                tx.markSettled();
                settlementTransactionRepository.save(tx);
            }
        };
    }
}
```

- [ ] **Step 7: SettlementScheduler 구현**

```java
// settlement-service/src/main/java/dev/settlement/batch/scheduler/SettlementScheduler.java
package dev.settlement.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class SettlementScheduler {

    private final JobLauncher jobLauncher;
    private final Job dailySettlementJob;

    @Scheduled(cron = "${settlement.schedule.cron:0 0 2 * * *}")
    public void runDailySettlement() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("settlementDate", LocalDate.now().toString())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            log.info("Starting daily settlement batch for date: {}", LocalDate.now());
            jobLauncher.run(dailySettlementJob, params);
            log.info("Daily settlement batch completed");
        } catch (Exception e) {
            log.error("Daily settlement batch failed", e);
        }
    }
}
```

- [ ] **Step 8: 테스트 실행 - 성공 확인**

Run: `cd settlement-service && ../gradlew test --tests "dev.settlement.batch.processor.SettlementFeeProcessorTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add settlement-service/src/main/java/dev/settlement/batch/ settlement-service/src/test/java/dev/settlement/batch/
git commit -m "feat(settlement-service): implement Spring Batch daily settlement job with fee calculation"
```

---

### Task 12: 정산 조회 및 수동 트리거 API

**Files:**
- Create: `settlement-service/src/main/java/dev/settlement/controller/SettlementController.java`
- Create: `settlement-service/src/main/java/dev/settlement/controller/dto/SettlementSummaryResponse.java`
- Create: `settlement-service/src/test/java/dev/settlement/controller/SettlementControllerTest.java`

- [ ] **Step 1: 테스트 작성 - SettlementController**

```java
// settlement-service/src/test/java/dev/settlement/controller/SettlementControllerTest.java
package dev.settlement.controller;

import dev.settlement.transaction.entity.SettlementTransaction;
import dev.settlement.transaction.entity.SettlementTransactionStatus;
import dev.settlement.transaction.repository.SettlementTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementController.class)
class SettlementControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private SettlementTransactionRepository repository;
    @MockBean private JobLauncher jobLauncher;
    @MockBean private Job dailySettlementJob;

    @Test
    void getSettlements_returnsPendingTransactions() throws Exception {
        SettlementTransaction tx = SettlementTransaction.fromPaymentEvent(
            "PG-TX-001", "M001", "CARD_AUTHORIZATION_SERVICE",
            BigDecimal.valueOf(10000), "KRW",
            LocalDateTime.of(2026, 3, 24, 14, 30)
        );
        when(repository.findByStatusAndSettlementDateLessThanEqual(
            SettlementTransactionStatus.PENDING, LocalDate.of(2026, 3, 25)))
            .thenReturn(List.of(tx));

        mockMvc.perform(get("/api/settlement/pending")
                .param("date", "2026-03-25"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].pgTransactionId").value("PG-TX-001"));
    }
}
```

- [ ] **Step 2: 테스트 실행 - 실패 확인**

Run: `cd settlement-service && ../gradlew test --tests "dev.settlement.controller.SettlementControllerTest"`
Expected: FAIL

- [ ] **Step 3: DTO 구현**

```java
// settlement-service/src/main/java/dev/settlement/controller/dto/SettlementSummaryResponse.java
package dev.settlement.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SettlementSummaryResponse(
    String pgTransactionId,
    String merchantId,
    String acquirerType,
    BigDecimal amount,
    String currency,
    BigDecimal feeAmount,
    BigDecimal netAmount,
    String status,
    LocalDate settlementDate
) {}
```

- [ ] **Step 4: Controller 구현**

```java
// settlement-service/src/main/java/dev/settlement/controller/SettlementController.java
package dev.settlement.controller;

import dev.settlement.controller.dto.SettlementSummaryResponse;
import dev.settlement.transaction.entity.SettlementTransaction;
import dev.settlement.transaction.entity.SettlementTransactionStatus;
import dev.settlement.transaction.repository.SettlementTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementTransactionRepository repository;
    private final JobLauncher jobLauncher;
    private final Job dailySettlementJob;

    @GetMapping("/pending")
    public List<SettlementSummaryResponse> getPendingSettlements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return repository.findByStatusAndSettlementDateLessThanEqual(
                SettlementTransactionStatus.PENDING, date)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/trigger")
    public String triggerSettlement() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("settlementDate", LocalDate.now().toString())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(dailySettlementJob, params);
            return "Settlement batch triggered successfully";
        } catch (Exception e) {
            log.error("Failed to trigger settlement batch", e);
            return "Settlement batch trigger failed: " + e.getMessage();
        }
    }

    private SettlementSummaryResponse toResponse(SettlementTransaction tx) {
        return new SettlementSummaryResponse(
            tx.getPgTransactionId(),
            tx.getMerchantId(),
            tx.getAcquirerType(),
            tx.getAmount(),
            tx.getCurrency(),
            tx.getFeeAmount(),
            tx.getNetAmount(),
            tx.getStatus().name(),
            tx.getSettlementDate()
        );
    }
}
```

- [ ] **Step 5: 테스트 실행 - 성공 확인**

Run: `cd settlement-service && ../gradlew test --tests "dev.settlement.controller.SettlementControllerTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add settlement-service/src/main/java/dev/settlement/controller/ settlement-service/src/test/java/dev/settlement/controller/
git commit -m "feat(settlement-service): add settlement query and manual trigger API"
```

---

## Phase 4: pg-service 리팩터링 (payment-service 역할 전환)

### Task 13: pg-service에서 원장 직접 관리 제거 → 이벤트 발행으로 전환

**Files:**
- Modify: `pg-service/src/main/java/dev/pg/approval/service/PgApprovalFacade.java`
- Modify: `pg-service/src/main/java/dev/pg/ledger/service/TransactionLedgerService.java`

이 Task에서는 pg-service가 더 이상 settlement_status를 직접 관리하지 않고, Kafka 이벤트를 통해 ledger-service와 settlement-service에 위임하도록 변경한다.

- [ ] **Step 1: PgApprovalFacade에서 이벤트 발행이 정상 동작하는지 기존 테스트 확인**

Run: `cd pg-service && ./gradlew test`
Expected: 모든 기존 테스트 PASS

- [ ] **Step 2: PgApprovalFacade 수정 - 이벤트 발행 통합**

기존 `PgApprovalFacade.approve()` 메서드에서:
1. `PaymentEventPublisher`를 의존성으로 추가
2. 승인 성공 후 `PaymentEventType.AUTHORIZED` 이벤트 발행
3. 승인 실패 후 `PaymentEventType.FAILED` 이벤트 발행

```java
// 기존 approve() 메서드의 성공 핸들링 후에 추가
paymentEventPublisher.publish(new PaymentEvent(
    UUID.randomUUID().toString(),
    PaymentEventType.AUTHORIZED,
    transaction.getPgTransactionId(),
    transaction.getMerchantTransactionId(),
    transaction.getMerchantId(),
    transaction.getMaskedCardNumber(),
    transaction.getAmount(),
    transaction.getCurrency(),
    transaction.getAcquirerType().name(),
    response.approvalNumber(),
    response.responseCode(),
    LocalDateTime.now()
));
```

- [ ] **Step 3: 기존 테스트 수정 - PaymentEventPublisher Mock 추가**

`PgApprovalFacadeTest.java`에서:
- `@Mock PaymentEventPublisher paymentEventPublisher` 추가
- 기존 테스트가 이벤트 발행을 포함하여 정상 동작하는지 확인

- [ ] **Step 4: 테스트 실행**

Run: `cd pg-service && ./gradlew test`
Expected: 모든 테스트 PASS

- [ ] **Step 5: Commit**

```bash
git add pg-service/src/main/java/dev/pg/approval/service/PgApprovalFacade.java pg-service/src/test/java/dev/pg/approval/service/PgApprovalFacadeTest.java
git commit -m "refactor(pg-service): integrate Kafka event publishing into approval flow"
```

---

### Task 14: 결제 상태머신 도입 (Stripe PaymentIntent 참고)

**Files:**
- Create: `pg-service/src/main/java/dev/pg/ledger/enums/PaymentStatus.java`
- Modify: `pg-service/src/main/java/dev/pg/ledger/entity/PaymentTransaction.java`
- Create: `pg-service/src/test/java/dev/pg/ledger/enums/PaymentStatusTest.java`

- [ ] **Step 1: 테스트 작성 - 상태 전이 규칙**

```java
// pg-service/src/test/java/dev/pg/ledger/enums/PaymentStatusTest.java
package dev.pg.ledger.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    @Test
    void pendingAuth_canTransitionTo_authorized() {
        assertThat(PaymentStatus.PENDING_AUTH.canTransitionTo(PaymentStatus.AUTHORIZED)).isTrue();
    }

    @Test
    void pendingAuth_canTransitionTo_failed() {
        assertThat(PaymentStatus.PENDING_AUTH.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void authorized_canTransitionTo_captured() {
        assertThat(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.CAPTURED)).isTrue();
    }

    @Test
    void authorized_canTransitionTo_voided() {
        assertThat(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.VOIDED)).isTrue();
    }

    @Test
    void captured_cannotTransitionTo_authorized() {
        assertThat(PaymentStatus.CAPTURED.canTransitionTo(PaymentStatus.AUTHORIZED)).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실행 - 실패 확인**

Run: `cd pg-service && ./gradlew test --tests "dev.pg.ledger.enums.PaymentStatusTest"`
Expected: FAIL

- [ ] **Step 3: PaymentStatus 상태머신 구현**

```java
// pg-service/src/main/java/dev/pg/ledger/enums/PaymentStatus.java
package dev.pg.ledger.enums;

import java.util.Set;

public enum PaymentStatus {
    CREATED(Set.of("PENDING_AUTH")),
    PENDING_AUTH(Set.of("AUTHORIZED", "FAILED", "TIMEOUT")),
    AUTHORIZED(Set.of("CAPTURED", "VOIDED")),
    CAPTURED(Set.of("SETTLED", "REFUND_PENDING")),
    SETTLED(Set.of("PAID_OUT", "REFUND_PENDING")),
    PAID_OUT(Set.of()),
    VOIDED(Set.of()),
    FAILED(Set.of()),
    TIMEOUT(Set.of()),
    REFUND_PENDING(Set.of("REFUNDED", "PARTIALLY_REFUNDED")),
    REFUNDED(Set.of()),
    PARTIALLY_REFUNDED(Set.of("REFUND_PENDING"));

    private final Set<String> allowedTransitions;

    PaymentStatus(Set<String> allowedTransitions) {
        this.allowedTransitions = allowedTransitions;
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return allowedTransitions.contains(target.name());
    }
}
```

- [ ] **Step 4: 테스트 실행 - 성공 확인**

Run: `cd pg-service && ./gradlew test --tests "dev.pg.ledger.enums.PaymentStatusTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add pg-service/src/main/java/dev/pg/ledger/enums/PaymentStatus.java pg-service/src/test/java/dev/pg/ledger/enums/PaymentStatusTest.java
git commit -m "feat(pg-service): add PaymentStatus state machine (Stripe PaymentIntent pattern)"
```

---

## Phase 5: API Gateway 라우팅 및 통합 테스트

### Task 15: API Gateway에 신규 서비스 라우팅 추가

**Files:**
- Modify: `api-gateway/src/main/resources/application.yaml`

- [ ] **Step 1: API Gateway에 ledger-service, settlement-service 라우팅 추가**

```yaml
# api-gateway/src/main/resources/application.yaml - routes에 추가
- id: ledger-service
  uri: lb://ledger-service
  predicates:
    - Path=/api/ledger/**

- id: settlement-service
  uri: lb://settlement-service
  predicates:
    - Path=/api/settlement/**
```

- [ ] **Step 2: Commit**

```bash
git add api-gateway/src/main/resources/application.yaml
git commit -m "feat(api-gateway): add routes for ledger-service and settlement-service"
```

---

### Task 16: docker-compose에 전체 서비스 통합

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: docker-compose.yml에 MySQL, 전체 서비스 추가**

```yaml
# docker-compose.yml에 추가
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
    ports:
      - "3306:3306"
    volumes:
      - ./init-db.sql:/docker-entrypoint-initdb.d/init.sql

  ledger-service:
    build: ./ledger-service
    ports:
      - "8082:8082"
    environment:
      MYSQL_URL: jdbc:mysql://mysql:3306/ledger_db
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      EUREKA_URL: http://eureka-server:8761/eureka/
    depends_on:
      - mysql
      - kafka
      - eureka-server

  settlement-service:
    build: ./settlement-service
    ports:
      - "8083:8083"
    environment:
      MYSQL_URL: jdbc:mysql://mysql:3306/settlement_db
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      EUREKA_URL: http://eureka-server:8761/eureka/
    depends_on:
      - mysql
      - kafka
      - eureka-server
```

- [ ] **Step 2: init-db.sql 생성 (DB 초기화 스크립트)**

```sql
-- init-db.sql
CREATE DATABASE IF NOT EXISTS pg_db;
CREATE DATABASE IF NOT EXISTS ledger_db;
CREATE DATABASE IF NOT EXISTS settlement_db;
CREATE DATABASE IF NOT EXISTS card_authorization_db;
CREATE DATABASE IF NOT EXISTS card_authorization_db_2;
CREATE DATABASE IF NOT EXISTS bank_db;
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml init-db.sql
git commit -m "infra: add full MSA docker-compose with MySQL, Kafka, and all services"
```

---

### Task 17: E2E 통합 테스트 시나리오 문서

**Files:**
- Create: `docs/phase7-settlement-msa-test-scenarios.md`

- [ ] **Step 1: 통합 테스트 시나리오 작성**

```markdown
# Phase 7: 정산 MSA 분리 통합 테스트 시나리오

## 시나리오 1: 결제 승인 → 원장 기록 → 정산 대기
1. POST /api/merchant/payment (Visa 카드)
2. payment-service 승인 → Kafka payment-events 발행
3. ledger-service: 복식부기 분개 생성 확인 (GET /api/ledger/journals/{pgTxId})
4. settlement-service: PENDING 정산 거래 생성 확인 (GET /api/settlement/pending?date=...)

## 시나리오 2: 일별 정산 배치 실행
1. POST /api/settlement/trigger
2. PENDING → PROCESSING → SETTLED 상태 전이 확인
3. 수수료(2.5%) 및 순정산금 계산 정확성 검증

## 시나리오 3: 다중 매입사 정산 분리
1. Visa 결제 + Mastercard 결제 각각 승인
2. 정산 배치 후 acquirerType별 정산 내역 확인

## 시나리오 4: 복식부기 정합성
1. 결제 승인 후 원장 조회
2. DR(고객미수금) + CR(가맹점미지급금) 합계 = 0 확인
```

- [ ] **Step 2: Commit**

```bash
git add docs/phase7-settlement-msa-test-scenarios.md
git commit -m "docs: add Phase 7 settlement MSA integration test scenarios"
```

---

## 요약: 전체 서비스 구조

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway (8000)                        │
├─────────────────────────────────────────────────────────────────┤
│  /api/merchant/**  → merchant-service (7070)                    │
│  /api/ledger/**    → ledger-service (8082)        [NEW]         │
│  /api/settlement/**→ settlement-service (8083)    [NEW]         │
└─────────────────┬───────────────────────────────────────────────┘
                  │
    ┌─────────────┴─────────────┐
    ▼                           ▼
merchant-service          payment-service (8081)   [RENAMED from pg-service]
    │                     │                 │
    │         ┌───────────┤          Kafka: payment-events
    │         ▼           ▼                 │
    │   card-auth-1  card-auth-2    ┌───────┴───────┐
    │   (Visa)       (MC/Amex)      ▼               ▼
    │         │           │    ledger-service   settlement-service
    │         └─────┬─────┘    (8082)           (8083)
    │               ▼          복식부기 원장     Spring Batch 정산
    │         bank-service     Square Books     우아한형제들 패턴
    │           (8080)         패턴
    │
    └── Eureka Server (8761)
```

### 핵심 설계 원칙

| 원칙 | 출처 | 적용 |
|------|------|------|
| **Stateless Edge Layer** | Adyen | payment-service는 상태를 최소한으로 유지, 이벤트로 위임 |
| **Immutable Double-Entry Ledger** | Square Books | ledger-service의 journal_entries는 INSERT-only |
| **T+N 배치 정산** | 우아한형제들 | settlement-service의 Spring Batch daily job |
| **PaymentIntent State Machine** | Stripe | PaymentStatus enum의 명시적 상태 전이 |
| **Event-Driven Decoupling** | Toss Payments | Kafka를 통한 서비스 간 느슨한 결합 |
| **Domain-Driven Separation** | 전체 PG 업계 | 결제/원장/정산 = 독립 Bounded Context |
