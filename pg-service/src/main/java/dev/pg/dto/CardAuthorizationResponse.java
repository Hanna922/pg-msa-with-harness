package dev.pg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardAuthorizationResponse {
    private String transactionId;
    private String approvalNumber;
    private String responseCode;
    private String message;
    private BigDecimal amount;
    private LocalDateTime authorizationDate;
    private boolean approved;
}
