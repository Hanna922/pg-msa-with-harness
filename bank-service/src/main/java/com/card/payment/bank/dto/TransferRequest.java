package com.card.payment.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 이체 요청 DTO
 * 요구사항: 11.3
 */
@Schema(description = "이체 요청")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    
    /**
     * 출금 계좌 번호
     */
    @Schema(description = "출금 계좌 번호 (카드사 정산 계좌)", example = "9999999999", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fromAccount;
    
    /**
     * 입금 계좌 번호
     */
    @Schema(description = "입금 계좌 번호 (가맹점 계좌)", example = "1234567890", requiredMode = Schema.RequiredMode.REQUIRED)
    private String toAccount;
    
    /**
     * 이체 금액
     */
    @Schema(description = "이체 금액", example = "950000", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
    
    /**
     * 정산 ID (정산 이체인 경우)
     */
    @Schema(description = "정산 ID (정산 이체인 경우)", example = "SETTLE20240214001")
    private String settlementId;
}
