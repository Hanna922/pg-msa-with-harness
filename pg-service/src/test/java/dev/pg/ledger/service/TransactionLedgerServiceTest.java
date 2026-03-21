package dev.pg.ledger.service;

import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.ledger.entity.PaymentTransaction;
import dev.pg.ledger.repository.PaymentTransactionRepository;
import dev.pg.routing.model.AcquirerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionLedgerServiceTest {

    private final PaymentTransactionRepository paymentTransactionRepository = mock(PaymentTransactionRepository.class);
    private final TransactionLedgerService transactionLedgerService =
            new TransactionLedgerService(paymentTransactionRepository);

    @Test
    void shouldPersistSelectedAcquirerTypeInPendingTransaction() {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603220001")
                .merchantId("MERCHANT-001")
                .cardNumber("5555555555554444")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();

        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentTransaction transaction = transactionLedgerService.createPendingTransaction(
                request,
                "PG202603220001ABCDEF",
                AcquirerType.CARD_AUTHORIZATION_SERVICE_2
        );

        assertEquals("PG202603220001ABCDEF", transaction.getPgTransactionId());
        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE_2, transaction.getAcquirerType());
        assertEquals("555555******4444", transaction.getMaskedCardNumber());
        assertNotNull(transaction.getRequestedAt());
    }
}
