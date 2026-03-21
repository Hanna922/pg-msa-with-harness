package dev.merchant.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String merchantTransactionId; // 가맹점 결제 고유 번호
    private String merchantId;

    private String pgTransactionId;       // PG사 결제 고유 번호
    private String cardNumber;            // 실제 환경에서는 마스킹 또는 토큰화 필요
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;         // 상태 (READY, APPROVED, FAILED)

    private String responseCode;          // PG 응답 코드
    private String responseMessage;       // PG 응답 메시지
    private LocalDateTime approvedAt;     // 승인 일시

    @Builder
    public Payment(String merchantTransactionId, String merchantId, String cardNumber, Integer amount, PaymentStatus status) {
        this.merchantTransactionId = merchantTransactionId;
        this.merchantId = merchantId;
        this.cardNumber = cardNumber;
        this.amount = amount;
        this.status = status;
    }

    public void updateStatus(PaymentStatus status, String pgTransactionId, String responseCode, String responseMessage, LocalDateTime approvedAt) {
        this.status = status;
        this.pgTransactionId = pgTransactionId;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.approvedAt = approvedAt;
    }
}