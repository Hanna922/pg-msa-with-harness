package com.card.payment.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 잔액 조회 요청 DTO
 * 요구사항: 4.2
 */
@Schema(description = "잔액 조회 요청")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceRequest {
    
    /**
     * 카드 번호 (암호화된 상태)
     */
    @Schema(description = "카드 번호 (암호화된 상태)", example = "1234567890123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cardNumber;
    
    /**
     * 요청 금액 (출금 가능 여부 확인용)
     */
    @Schema(description = "요청 금액 (출금 가능 여부 확인용)", example = "50000", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
}
