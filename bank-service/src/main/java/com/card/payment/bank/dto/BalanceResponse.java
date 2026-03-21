package com.card.payment.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 잔액 조회 응답 DTO
 * 요구사항: 4.2
 */
@Schema(description = "잔액 조회 응답")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {
    
    /**
     * 계좌 번호
     */
    @Schema(description = "계좌 번호", example = "1234567890")
    private String accountNumber;
    
    /**
     * 현재 잔액
     */
    @Schema(description = "현재 잔액", example = "1000000")
    private BigDecimal balance;
    
    /**
     * 최소 잔액
     */
    @Schema(description = "최소 잔액", example = "0")
    private BigDecimal minimumBalance;
    
    /**
     * 출금 가능 금액
     */
    @Schema(description = "출금 가능 금액", example = "1000000")
    private BigDecimal availableBalance;
    
    /**
     * 요청 금액
     */
    @Schema(description = "요청 금액", example = "50000")
    private BigDecimal requestAmount;
    
    /**
     * 출금 가능 여부
     */
    @Schema(description = "출금 가능 여부", example = "true")
    private boolean canWithdraw;
    
    /**
     * 응답 코드 (00: 성공, 51: 잔액 부족)
     */
    @Schema(description = "응답 코드 (00: 성공, 51: 잔액 부족, 96: 시스템 오류)", example = "00")
    private String responseCode;
    
    /**
     * 응답 메시지
     */
    @Schema(description = "응답 메시지", example = "승인")
    private String responseMessage;
}
