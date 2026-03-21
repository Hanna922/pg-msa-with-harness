package dev.pg.ledger.service;

import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.ledger.entity.PaymentTransaction;
import dev.pg.ledger.enums.ApprovalStatus;
import dev.pg.ledger.enums.SettlementStatus;
import dev.pg.ledger.repository.PaymentTransactionRepository;
import dev.pg.support.util.CardMaskingUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TransactionLedgerService {

    private final PaymentTransactionRepository paymentTransactionRepository;

    public TransactionLedgerService(PaymentTransactionRepository paymentTransactionRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    public PaymentTransaction createPendingTransaction(MerchantApprovalRequest request, String pgTransactionId) {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .pgTransactionId(pgTransactionId)
                .merchantTransactionId(request.getMerchantTransactionId())
                .merchantId(request.getMerchantId())
                .maskedCardNumber(CardMaskingUtils.mask(request.getCardNumber()))
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .approvalStatus(ApprovalStatus.PENDING)
                .settlementStatus(SettlementStatus.NOT_READY)
                .requestedAt(LocalDateTime.now())
                .build();

        return paymentTransactionRepository.save(transaction);
    }

    public PaymentTransaction markApproved(PaymentTransaction transaction, CardAuthorizationResponse response) {
        LocalDateTime approvedAt = response.getAuthorizationDate() != null
                ? response.getAuthorizationDate()
                : LocalDateTime.now();
        transaction.markApproved(
                response.getResponseCode(),
                response.getMessage(),
                response.getApprovalNumber(),
                approvedAt
        );
        return paymentTransactionRepository.save(transaction);
    }

    public PaymentTransaction markFailed(PaymentTransaction transaction, CardAuthorizationResponse response) {
        transaction.markFailed(
                response.getResponseCode(),
                response.getMessage(),
                LocalDateTime.now()
        );
        return paymentTransactionRepository.save(transaction);
    }

    public PaymentTransaction markTimeout(PaymentTransaction transaction, String message) {
        transaction.markTimeout("96", message, LocalDateTime.now());
        return paymentTransactionRepository.save(transaction);
    }
}
