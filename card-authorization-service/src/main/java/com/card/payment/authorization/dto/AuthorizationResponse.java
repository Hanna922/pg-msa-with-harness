package com.card.payment.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 승인 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "카드 승인 응답")
public class AuthorizationResponse {
    
    /**
     * 거래 고유 번호
     */
    @Schema(description = "거래 고유 번호", example = "TXN-CP-SUCCESS-001")
    private String transactionId;
    
    /**
     * 승인 번호 (승인 성공 시)
     */
    @Schema(description = "승인 번호 (8자리, 승인 성공 시에만 발급)", example = "12345678")
    private String approvalNumber;
    
    /**
     * 응답 코드
     * 00: 승인
     * 14: 카드정지
     * 51: 잔액부족
     * 54: 유효기간만료
     * 55: PIN오류
     * 61: 한도초과
     * 94: 중복거래
     * 96: 시스템오류
     */
    @Schema(description = "응답 코드", example = "00", 
            allowableValues = {"00", "14", "51", "54", "55", "61", "94", "96"})
    private String responseCode;
    
    /**
     * 응답 메시지
     */
    @Schema(description = "응답 메시지", example = "승인")
    private String message;
    
    /**
     * 거래 금액
     */
    @Schema(description = "거래 금액", example = "10000")
    private BigDecimal amount;
    
    /**
     * 승인 일시
     */
    @Schema(description = "승인 처리 일시", example = "2026-02-14T16:30:00")
    private LocalDateTime authorizationDate;
    
    /**
     * 승인 성공 여부
     */
    @Schema(description = "승인 성공 여부", example = "true")
    private boolean approved;
}
