package dev.merchant.client;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PgAuthResponse {
    private String merchantTransactionId;
    private String pgTransactionId;
    private boolean approved;
    private String responseCode;
    private String message;
    private String approvalNumber;
    private String approvedAt;
}