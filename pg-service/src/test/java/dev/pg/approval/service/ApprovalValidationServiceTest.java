package dev.pg.approval.service;

import dev.pg.dto.MerchantApprovalRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovalValidationServiceTest {

    private final ApprovalValidationService approvalValidationService = new ApprovalValidationService();

    @Test
    void shouldAcceptValidRequest() {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603210001")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();

        assertDoesNotThrow(() -> approvalValidationService.validate(request));
    }

    @Test
    void shouldRejectMissingMerchantTransactionId() {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> approvalValidationService.validate(request)
        );

        assertEquals("merchantTransactionId is required", exception.getMessage());
    }

    @Test
    void shouldRejectNonPositiveAmount() {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603210001")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(BigDecimal.ZERO)
                .currency("KRW")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> approvalValidationService.validate(request)
        );

        assertEquals("amount must be greater than zero", exception.getMessage());
    }
}
