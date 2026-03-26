package dev.ledger.service;

import dev.ledger.dto.CreateLedgerTransactionRequest;
import dev.ledger.dto.LedgerTransactionResponse;
import dev.ledger.entity.LedgerTransactionRecord;
import dev.ledger.entity.SettlementStatus;
import dev.ledger.repository.LedgerTransactionRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerTransactionService {

    private final LedgerTransactionRecordRepository repository;

    public LedgerTransactionResponse createOrUpdate(CreateLedgerTransactionRequest request) {
        LedgerTransactionRecord record = repository.findByPgTransactionId(request.getPgTransactionId())
                .map(existing -> updateExisting(existing, request))
                .orElseGet(() -> createNew(request));

        return toResponse(repository.save(record));
    }

    public LedgerTransactionResponse getByPgTransactionId(String pgTransactionId) {
        return repository.findByPgTransactionId(pgTransactionId)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Ledger transaction not found: " + pgTransactionId));
    }

    public List<LedgerTransactionResponse> findTransactions(
            String merchantId,
            String approvalStatus,
            String settlementStatus,
            LocalDateTime approvedFrom,
            LocalDateTime approvedTo
    ) {
        Specification<LedgerTransactionRecord> spec = (root, query, cb) -> cb.conjunction();

        if (merchantId != null && !merchantId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("merchantId"), merchantId));
        }
        if (approvalStatus != null && !approvalStatus.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("approvalStatus"), Enum.valueOf(dev.ledger.entity.ApprovalStatus.class, approvalStatus)));
        }
        if (settlementStatus != null && !settlementStatus.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("settlementStatus"), Enum.valueOf(SettlementStatus.class, settlementStatus)));
        }
        if (approvedFrom != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("approvedAt"), approvedFrom));
        }
        if (approvedTo != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("approvedAt"), approvedTo));
        }

        return repository.findAll(spec).stream().map(this::toResponse).toList();
    }

    public LedgerTransactionResponse updateSettlementStatus(String pgTransactionId, SettlementStatus settlementStatus) {
        LedgerTransactionRecord record = repository.findByPgTransactionId(pgTransactionId)
                .orElseThrow(() -> new EntityNotFoundException("Ledger transaction not found: " + pgTransactionId));

        validateSettlementTransition(record.getSettlementStatus(), settlementStatus);
        record.setSettlementStatus(settlementStatus);
        record.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(record));
    }

    private LedgerTransactionRecord createNew(CreateLedgerTransactionRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return LedgerTransactionRecord.builder()
                .pgTransactionId(request.getPgTransactionId())
                .merchantTransactionId(request.getMerchantTransactionId())
                .merchantId(request.getMerchantId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .approvalStatus(request.getApprovalStatus())
                .settlementStatus(request.getSettlementStatus())
                .acquirerType(request.getAcquirerType())
                .responseCode(request.getResponseCode())
                .message(request.getMessage())
                .approvalNumber(request.getApprovalNumber())
                .approvedAt(request.getApprovedAt())
                .recordedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private LedgerTransactionRecord updateExisting(LedgerTransactionRecord existing, CreateLedgerTransactionRequest request) {
        existing.setMerchantTransactionId(request.getMerchantTransactionId());
        existing.setMerchantId(request.getMerchantId());
        existing.setAmount(request.getAmount());
        existing.setCurrency(request.getCurrency());
        existing.setApprovalStatus(request.getApprovalStatus());
        existing.setSettlementStatus(request.getSettlementStatus());
        existing.setAcquirerType(request.getAcquirerType());
        existing.setResponseCode(request.getResponseCode());
        existing.setMessage(request.getMessage());
        existing.setApprovalNumber(request.getApprovalNumber());
        existing.setApprovedAt(request.getApprovedAt());
        existing.setUpdatedAt(LocalDateTime.now());
        if (existing.getRecordedAt() == null) {
            existing.setRecordedAt(LocalDateTime.now());
        }
        return existing;
    }

    private void validateSettlementTransition(SettlementStatus current, SettlementStatus target) {
        if (current == target) {
            return;
        }

        boolean valid = switch (current) {
            case NOT_READY -> target == SettlementStatus.READY || target == SettlementStatus.SETTLED;
            case READY -> target == SettlementStatus.SETTLED;
            case SETTLED -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException("Invalid settlement status transition: " + current + " -> " + target);
        }
    }

    private LedgerTransactionResponse toResponse(LedgerTransactionRecord record) {
        return LedgerTransactionResponse.builder()
                .pgTransactionId(record.getPgTransactionId())
                .merchantTransactionId(record.getMerchantTransactionId())
                .merchantId(record.getMerchantId())
                .amount(record.getAmount())
                .currency(record.getCurrency())
                .approvalStatus(record.getApprovalStatus())
                .settlementStatus(record.getSettlementStatus())
                .acquirerType(record.getAcquirerType())
                .responseCode(record.getResponseCode())
                .message(record.getMessage())
                .approvalNumber(record.getApprovalNumber())
                .approvedAt(record.getApprovedAt())
                .recordedAt(record.getRecordedAt())
                .build();
    }
}
