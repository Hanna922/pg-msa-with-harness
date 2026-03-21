package dev.pg.service;

import dev.pg.client.CardAuthorizationClient;
import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PgApprovalServiceTest {

    private final CardAuthorizationClient client = mock(CardAuthorizationClient.class);
    private final PgApprovalService service = new PgApprovalService(client);

    @Test
    void shouldMapCardAuthorizationResponseToMerchantResponse() {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .mti("0100")
                .processingCode("000000")
                .transmissionDateTime("20260319153000")
                .stan("123456")
                .build();

        when(client.authorize(any())).thenReturn(CardAuthorizationResponse.builder()
                .transactionId("PG202603190001ABCDEF")
                .approvalNumber("12345678")
                .responseCode("00")
                .message("Approved")
                .amount(new BigDecimal("10000"))
                .authorizationDate(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .approved(true)
                .build());

        MerchantApprovalResponse response = service.approve(request);

        assertEquals("M202603190001", response.getMerchantTransactionId());
        assertNotNull(response.getPgTransactionId());
        assertTrue(response.isApproved());
        assertEquals("00", response.getResponseCode());
        assertEquals("12345678", response.getApprovalNumber());
        assertNotNull(response.getApprovedAt());
    }

    @Test
    void shouldValidateRequiredFields() {
        MerchantApprovalRequest invalidRequest = MerchantApprovalRequest.builder().build();

        assertThrows(IllegalArgumentException.class, () -> service.approve(invalidRequest));
    }
}
