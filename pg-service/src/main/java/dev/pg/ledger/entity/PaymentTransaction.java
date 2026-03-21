package dev.pg.ledger.entity;

import dev.pg.ledger.enums.ApprovalStatus;
import dev.pg.ledger.enums.SettlementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @Column(nullable = false, length = 32)
    private String pgTransactionId;

    @Column(nullable = false, unique = true, length = 64)
    private String merchantTransactionId;

    @Column(nullable = false, length = 64)
    private String merchantId;

    @Column(nullable = false, length = 32)
    private String maskedCardNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApprovalStatus approvalStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SettlementStatus settlementStatus;

    @Column(length = 8)
    private String responseCode;

    @Column(length = 255)
    private String message;

    @Column(length = 32)
    private String approvalNumber;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime respondedAt;

    private LocalDateTime approvedAt;

    public void markApproved(String responseCode, String message, String approvalNumber, LocalDateTime approvedAt) {
        this.approvalStatus = ApprovalStatus.APPROVED;
        this.settlementStatus = SettlementStatus.READY;
        this.responseCode = responseCode;
        this.message = message;
        this.approvalNumber = approvalNumber;
        this.respondedAt = approvedAt;
        this.approvedAt = approvedAt;
    }

    public void markFailed(String responseCode, String message, LocalDateTime respondedAt) {
        this.approvalStatus = ApprovalStatus.FAILED;
        this.responseCode = responseCode;
        this.message = message;
        this.respondedAt = respondedAt;
    }

    public void markTimeout(String responseCode, String message, LocalDateTime respondedAt) {
        this.approvalStatus = ApprovalStatus.TIMEOUT;
        this.responseCode = responseCode;
        this.message = message;
        this.respondedAt = respondedAt;
    }
}
