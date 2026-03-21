package dev.merchant.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PgAuthRequest {
    private String merchantTransactionId;
    private String merchantId;
    private String cardNumber;
    private String expiryDate;
    private Integer amount;
    private String currency;
    private String mti;
    private String processingCode;
    private String transmissionDateTime;
    private String stan;
}