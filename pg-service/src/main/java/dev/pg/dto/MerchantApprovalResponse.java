package dev.pg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantApprovalResponse {
    private String merchantTransactionId;
    private String pgTransactionId;
    private boolean approved;
    private String responseCode;
    private String message;
    private String approvalNumber;
    private LocalDateTime approvedAt;
}
