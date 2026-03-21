package com.card.payment.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 이체 응답 DTO
 * 요구사항: 11.11
 */
@Schema(description = "이체 응답")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
    
    /**
     * 성공 여부
     */
    @Schema(description = "성공 여부", example = "true")
    private boolean success;
    
    /**
     * 이체 고유 번호
     */
    @Schema(description = "이체 고유 번호", example = "TRF20240214123456789")
    private String transferId;
    
    /**
     * 출금 계좌 번호
     */
    @Schema(description = "출금 계좌 번호", example = "9999999999")
    private String fromAccount;
    
    /**
     * 입금 계좌 번호
     */
    @Schema(description = "입금 계좌 번호", example = "1234567890")
    private String toAccount;
    
    /**
     * 이체 금액
     */
    @Schema(description = "이체 금액", example = "950000")
    private BigDecimal amount;
    
    /**
     * 이체 일시
     */
    @Schema(description = "이체 일시", example = "2024-02-14T12:34:56")
    private LocalDateTime transferDate;
    
    /**
     * 응답 코드 (00: 성공, 기타: 오류)
     */
    @Schema(description = "응답 코드 (00: 성공, 96: 시스템 오류)", example = "00")
    private String responseCode;
    
    /**
     * 응답 메시지
     */
    @Schema(description = "응답 메시지", example = "이체 성공")
    private String responseMessage;
    
    /**
     * 실패 사유 (실패 시)
     */
    @Schema(description = "실패 사유 (실패 시)")
    private String failureReason;
}
