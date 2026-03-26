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
public class CardAuthorizationRequest {
    private String transactionId;
    private String cardNumber;
    private BigDecimal amount;
    private String merchantId;
    private String terminalId;
    private String pin;
}
