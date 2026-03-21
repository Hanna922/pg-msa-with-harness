package dev.pg.service;

import dev.pg.client.CardAuthorizationClient;
import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import dev.pg.ledger.entity.PaymentTransaction;
import dev.pg.ledger.enums.ApprovalStatus;
import dev.pg.ledger.enums.SettlementStatus;
import dev.pg.ledger.service.IdempotencyService;
import dev.pg.ledger.service.TransactionLedgerService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PgApprovalServiceTest {

    private final CardAuthorizationClient client = mock(CardAuthorizationClient.class);
    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final TransactionLedgerService transactionLedgerService = mock(TransactionLedgerService.class);
    private final PgApprovalService service =
            new PgApprovalService(client, idempotencyService, transactionLedgerService);

    @Test
    void shouldPersistAndMapApprovedResponse() {
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

        PaymentTransaction pendingTransaction = PaymentTransaction.builder()
                .pgTransactionId("PG202603190001ABCDEF")
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .approvalStatus(ApprovalStatus.PENDING)
                .settlementStatus(SettlementStatus.NOT_READY)
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 0))
                .build();
        PaymentTransaction approvedTransaction = PaymentTransaction.builder()
                .pgTransactionId("PG202603190001ABCDEF")
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .approvalStatus(ApprovalStatus.APPROVED)
                .settlementStatus(SettlementStatus.READY)
                .responseCode("00")
                .message("Approved")
                .approvalNumber("12345678")
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 0))
                .respondedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .approvedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .build();

        when(idempotencyService.findExistingTransaction("M202603190001")).thenReturn(Optional.empty());
        when(transactionLedgerService.createPendingTransaction(request, "PG202603190001ABCDEF"))
                .thenReturn(pendingTransaction);
        when(transactionLedgerService.markApproved(any(), any())).thenReturn(approvedTransaction);
        when(client.authorize(any())).thenReturn(CardAuthorizationResponse.builder()
                .transactionId("PG202603190001ABCDEF")
                .approvalNumber("12345678")
                .responseCode("00")
                .message("Approved")
                .amount(new BigDecimal("10000"))
                .authorizationDate(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .approved(true)
                .build());

        PgApprovalService spyService = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doReturn("PG202603190001ABCDEF").when(spyService).generatePgTransactionId();

        MerchantApprovalResponse response = spyService.approve(request);

        assertEquals("M202603190001", response.getMerchantTransactionId());
        assertEquals("PG202603190001ABCDEF", response.getPgTransactionId());
        assertTrue(response.isApproved());
        assertEquals("00", response.getResponseCode());
        assertEquals("12345678", response.getApprovalNumber());
        assertEquals(LocalDateTime.of(2026, 3, 19, 15, 30, 5), response.getApprovedAt());
    }

    @Test
    void shouldReturnExistingTransactionForDuplicateMerchantTransactionId() {
        PaymentTransaction existingTransaction = PaymentTransaction.builder()
                .pgTransactionId("PG202603190001ABCDEF")
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .approvalStatus(ApprovalStatus.APPROVED)
                .settlementStatus(SettlementStatus.READY)
                .responseCode("00")
                .message("Approved")
                .approvalNumber("12345678")
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 0))
                .respondedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .approvedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .build();
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();

        when(idempotencyService.findExistingTransaction("M202603190001"))
                .thenReturn(Optional.of(existingTransaction));

        MerchantApprovalResponse response = service.approve(request);

        assertEquals("PG202603190001ABCDEF", response.getPgTransactionId());
        assertTrue(response.isApproved());
        verifyNoInteractions(client, transactionLedgerService);
    }

    @Test
    void shouldValidateRequiredFields() {
        MerchantApprovalRequest invalidRequest = MerchantApprovalRequest.builder().build();

        assertThrows(IllegalArgumentException.class, () -> service.approve(invalidRequest));
    }
}
