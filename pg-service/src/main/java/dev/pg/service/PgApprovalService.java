package dev.pg.service;

import dev.pg.approval.service.ApprovalValidationService;
import dev.pg.client.CardAuthorizationClient;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import dev.pg.ledger.entity.PaymentTransaction;
import dev.pg.ledger.enums.ApprovalStatus;
import dev.pg.ledger.service.IdempotencyService;
import dev.pg.ledger.service.TransactionLedgerService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Service
public class PgApprovalService {

    private static final DateTimeFormatter PG_TX_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CardAuthorizationClient cardAuthorizationClient;
    private final ApprovalValidationService approvalValidationService;
    private final IdempotencyService idempotencyService;
    private final TransactionLedgerService transactionLedgerService;

    public PgApprovalService(
            CardAuthorizationClient cardAuthorizationClient,
            ApprovalValidationService approvalValidationService,
            IdempotencyService idempotencyService,
            TransactionLedgerService transactionLedgerService
    ) {
        this.cardAuthorizationClient = cardAuthorizationClient;
        this.approvalValidationService = approvalValidationService;
        this.idempotencyService = idempotencyService;
        this.transactionLedgerService = transactionLedgerService;
    }

    public MerchantApprovalResponse approve(MerchantApprovalRequest request) {
        approvalValidationService.validate(request);

        Optional<PaymentTransaction> existingTransaction =
                idempotencyService.findExistingTransaction(request.getMerchantTransactionId());
        if (existingTransaction.isPresent()) {
            return mapFromTransaction(existingTransaction.get());
        }

        String pgTransactionId = generatePgTransactionId();
        PaymentTransaction transaction = transactionLedgerService.createPendingTransaction(request, pgTransactionId);
        CardAuthorizationRequest cardRequest = CardAuthorizationRequest.builder()
                .transactionId(pgTransactionId)
                .cardNumber(request.getCardNumber())
                .amount(request.getAmount())
                .merchantId(request.getMerchantId())
                .terminalId(null)
                .pin(null)
                .build();

        try {
            CardAuthorizationResponse cardResponse = cardAuthorizationClient.authorize(cardRequest);
            PaymentTransaction updatedTransaction = cardResponse.isApproved()
                    ? transactionLedgerService.markApproved(transaction, cardResponse)
                    : transactionLedgerService.markFailed(transaction, cardResponse);
            return mapFromTransaction(updatedTransaction);
        } catch (Exception e) {
            PaymentTransaction timedOutTransaction =
                    transactionLedgerService.markTimeout(transaction, "PG approval processing failed");
            return mapFromTransaction(timedOutTransaction);
        }
    }

    MerchantApprovalResponse mapFromTransaction(PaymentTransaction transaction) {
        return MerchantApprovalResponse.builder()
                .merchantTransactionId(transaction.getMerchantTransactionId())
                .pgTransactionId(transaction.getPgTransactionId())
                .approved(transaction.getApprovalStatus() == ApprovalStatus.APPROVED)
                .responseCode(transaction.getResponseCode())
                .message(transaction.getMessage())
                .approvalNumber(transaction.getApprovalNumber())
                .approvedAt(transaction.getApprovedAt() != null ? transaction.getApprovedAt() : transaction.getRespondedAt())
                .build();
    }

    String generatePgTransactionId() {
        String timestamp = LocalDateTime.now().format(PG_TX_TIMESTAMP);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "PG" + timestamp + suffix;
    }
}
