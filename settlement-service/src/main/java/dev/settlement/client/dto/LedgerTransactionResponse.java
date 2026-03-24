package dev.settlement.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransactionResponse {
    private String pgTransactionId;
    private String merchantTransactionId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private ApprovalStatus approvalStatus;
    private SettlementStatus settlementStatus;
    private String acquirerType;
    private String responseCode;
    private String message;
    private String approvalNumber;
    private LocalDateTime approvedAt;
    private LocalDateTime recordedAt;
}
