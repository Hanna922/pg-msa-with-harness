package dev.merchant.dto;

import dev.merchant.entity.Payment;
import dev.merchant.entity.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponseDto {
    private String merchantTransactionId;
    private String merchantId;
    private String pgTransactionId;
    private Integer amount;
    private String status;
    private String responseCode;
    private String message;
    private LocalDateTime approvedAt;
    private boolean approved;

    public static PaymentResponseDto from(Payment payment) {
        return PaymentResponseDto.builder()
                .merchantTransactionId(payment.getMerchantTransactionId())
                .merchantId(payment.getMerchantId())
                .pgTransactionId(payment.getPgTransactionId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .responseCode(payment.getResponseCode())
                .message(payment.getResponseMessage())
                .approvedAt(payment.getApprovedAt())
                .approved(payment.getStatus() == PaymentStatus.APPROVED)
                .build();
    }
}
