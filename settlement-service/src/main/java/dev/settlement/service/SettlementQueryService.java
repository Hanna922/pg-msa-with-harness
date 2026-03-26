package dev.settlement.service;

import dev.settlement.dto.SettlementResponse;
import dev.settlement.entity.SettlementTransaction;
import dev.settlement.repository.SettlementTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SettlementQueryService {

    private final SettlementTransactionRepository repository;

    public SettlementQueryService(SettlementTransactionRepository repository) {
        this.repository = repository;
    }

    public List<SettlementResponse> findAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public SettlementResponse getBySettlementId(String settlementId) {
        return repository.findById(settlementId)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Settlement not found: " + settlementId));
    }

    private SettlementResponse toResponse(SettlementTransaction transaction) {
        return SettlementResponse.builder()
                .settlementId(transaction.getSettlementId())
                .pgTransactionId(transaction.getPgTransactionId())
                .merchantId(transaction.getMerchantId())
                .amount(transaction.getAmount())
                .feeAmount(transaction.getFeeAmount())
                .netAmount(transaction.getNetAmount())
                .currency(transaction.getCurrency())
                .settlementDate(transaction.getSettlementDate())
                .status(transaction.getStatus().name())
                .build();
    }
}
