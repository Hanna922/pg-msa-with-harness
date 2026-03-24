package dev.pg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantApprovalRequest {
    private String merchantTransactionId;
    private String merchantId;
    private String cardNumber;
    private String expiryDate;
    private BigDecimal amount;
    private String currency;
    private String mti;
    private String processingCode;
    private String transmissionDateTime;
    private String stan;
}
