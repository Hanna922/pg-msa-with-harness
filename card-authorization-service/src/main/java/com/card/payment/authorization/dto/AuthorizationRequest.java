package com.card.payment.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 승인 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "카드 승인 요청")
public class AuthorizationRequest {
    
    /**
     * 거래 고유 번호
     */
    @Schema(description = "거래 고유 번호", example = "TXN-CP-SUCCESS-001", required = true)
    private String transactionId;
    
    /**
     * 카드 번호
     */
    @Schema(description = "카드 번호 (16자리)", example = "4111111111111111", required = true)
    private String cardNumber;
    
    /**
     * 거래 금액
     */
    @Schema(description = "거래 금액", example = "10000", required = true)
    private BigDecimal amount;
    
    /**
     * 가맹점 ID
     */
    @Schema(description = "가맹점 ID", example = "MERCHANT-001", required = true)
    private String merchantId;
    
    /**
     * 단말기 ID
     */
    @Schema(description = "단말기 ID", example = "TERMINAL-001")
    private String terminalId;
    
    /**
     * PIN (선택 사항)
     */
    @Schema(description = "PIN (4자리, 선택 사항)", example = "1234")
    private String pin;
}
