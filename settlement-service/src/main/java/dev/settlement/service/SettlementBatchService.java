package dev.settlement.service;

import dev.settlement.client.LedgerClient;
import dev.settlement.client.dto.LedgerTransactionResponse;
import dev.settlement.entity.SettlementStatus;
import dev.settlement.entity.SettlementTransaction;
import dev.settlement.repository.SettlementTransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class SettlementBatchService {

    private final LedgerClient ledgerClient;
    private final SettlementTransactionRepository repository;
    private final SettlementCalculationService calculationService;

    public SettlementBatchService(
            LedgerClient ledgerClient,
            SettlementTransactionRepository repository,
            SettlementCalculationService calculationService
    ) {
        this.ledgerClient = ledgerClient;
        this.repository = repository;
        this.calculationService = calculationService;
    }

    public SettlementRunResult run(LocalDate settlementDate) {
        List<LedgerTransactionResponse> transactions = ledgerClient.findTransactionsForSettlement(
                null,
                "APPROVED",
                "NOT_READY",
                null,
                null
        );

        int createdCount = 0;
        for (LedgerTransactionResponse transaction : transactions) {
            if (repository.findByPgTransactionId(transaction.getPgTransactionId()).isPresent()) {
                continue;
            }

            ledgerClient.markTransactionAsSettled(transaction.getPgTransactionId());
            repository.save(buildSettlementTransaction(transaction, settlementDate));
            createdCount++;
        }

        return new SettlementRunResult(transactions.size(), createdCount);
    }

    private SettlementTransaction buildSettlementTransaction(
            LedgerTransactionResponse transaction,
            LocalDate settlementDate
    ) {
        return SettlementTransaction.builder()
                .settlementId("STL-" + UUID.randomUUID())
                .pgTransactionId(transaction.getPgTransactionId())
                .merchantId(transaction.getMerchantId())
                .amount(transaction.getAmount())
                .feeAmount(calculationService.calculateFeeAmount(transaction.getAmount()))
                .netAmount(calculationService.calculateNetAmount(transaction.getAmount()))
                .currency(transaction.getCurrency())
                .settlementDate(settlementDate)
                .status(SettlementStatus.SETTLED)
                .build();
    }
}
