package dev.settlement.service;

import dev.settlement.client.LedgerClient;
import dev.settlement.client.dto.ApprovalStatus;
import dev.settlement.client.dto.LedgerTransactionResponse;
import dev.settlement.entity.SettlementTransaction;
import dev.settlement.repository.SettlementTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementBatchServiceTest {

    private final LedgerClient ledgerClient = mock(LedgerClient.class);
    private final SettlementTransactionRepository repository = mock(SettlementTransactionRepository.class);
    private final SettlementCalculationService calculationService = new SettlementCalculationService(new BigDecimal("0.03"));
    private final SettlementBatchService service = new SettlementBatchService(ledgerClient, repository, calculationService);

    @Test
    void run_createsSettlementAndUpdatesLedgerStatus() {
        LedgerTransactionResponse transaction = LedgerTransactionResponse.builder()
                .pgTransactionId("PG20260324A1B2C3")
                .merchantTransactionId("M-001")
                .merchantId("MERCHANT-001")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .approvalStatus(ApprovalStatus.APPROVED)
                .settlementStatus(dev.settlement.client.dto.SettlementStatus.NOT_READY)
                .approvedAt(LocalDateTime.of(2026, 3, 24, 12, 0))
                .build();

        when(ledgerClient.findTransactionsForSettlement(null, "APPROVED", "NOT_READY", null, null))
                .thenReturn(List.of(transaction));
        when(repository.findByPgTransactionId("PG20260324A1B2C3")).thenReturn(Optional.empty());

        SettlementRunResult result = service.run(LocalDate.of(2026, 3, 25));

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.createdCount()).isEqualTo(1);
        verify(repository).save(argThat(saved ->
                saved.getPgTransactionId().equals("PG20260324A1B2C3")
                        && saved.getMerchantId().equals("MERCHANT-001")
                        && saved.getAmount().compareTo(new BigDecimal("10000")) == 0
                        && saved.getFeeAmount().compareTo(new BigDecimal("300.00")) == 0
                        && saved.getNetAmount().compareTo(new BigDecimal("9700.00")) == 0
                        && saved.getSettlementDate().equals(LocalDate.of(2026, 3, 25))
                        && saved.getStatus() == dev.settlement.entity.SettlementStatus.SETTLED
        ));
        verify(ledgerClient).markTransactionAsSettled("PG20260324A1B2C3");
    }

    @Test
    void run_skipsAlreadySettledTransaction() {
        LedgerTransactionResponse transaction = LedgerTransactionResponse.builder()
                .pgTransactionId("PG20260324A1B2C3")
                .merchantId("MERCHANT-001")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .approvalStatus(ApprovalStatus.APPROVED)
                .settlementStatus(dev.settlement.client.dto.SettlementStatus.NOT_READY)
                .approvedAt(LocalDateTime.of(2026, 3, 24, 12, 0))
                .build();

        when(ledgerClient.findTransactionsForSettlement(null, "APPROVED", "NOT_READY", null, null))
                .thenReturn(List.of(transaction));
        when(repository.findByPgTransactionId("PG20260324A1B2C3"))
                .thenReturn(Optional.of(mock(SettlementTransaction.class)));

        SettlementRunResult result = service.run(LocalDate.of(2026, 3, 25));

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.createdCount()).isZero();
        verify(repository, never()).save(any());
        verify(ledgerClient, never()).markTransactionAsSettled(any());
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
