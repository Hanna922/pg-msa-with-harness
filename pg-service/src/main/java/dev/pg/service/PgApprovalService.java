package dev.pg.service;

import dev.pg.approval.mapper.ApprovalMapper;
import dev.pg.approval.mapper.CardAuthorizationRequestFactory;
import dev.pg.approval.service.ApprovalValidationService;
import dev.pg.client.CardAuthorizationClient;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import dev.pg.ledger.entity.PaymentTransaction;
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
    private final ApprovalMapper approvalMapper;
    private final CardAuthorizationRequestFactory cardAuthorizationRequestFactory;
    private final ApprovalValidationService approvalValidationService;
    private final IdempotencyService idempotencyService;
    private final TransactionLedgerService transactionLedgerService;

    public PgApprovalService(
            CardAuthorizationClient cardAuthorizationClient,
            ApprovalMapper approvalMapper,
            CardAuthorizationRequestFactory cardAuthorizationRequestFactory,
            ApprovalValidationService approvalValidationService,
            IdempotencyService idempotencyService,
            TransactionLedgerService transactionLedgerService
    ) {
        this.cardAuthorizationClient = cardAuthorizationClient;
        this.approvalMapper = approvalMapper;
        this.cardAuthorizationRequestFactory = cardAuthorizationRequestFactory;
        this.approvalValidationService = approvalValidationService;
        this.idempotencyService = idempotencyService;
        this.transactionLedgerService = transactionLedgerService;
    }

    public MerchantApprovalResponse approve(MerchantApprovalRequest request) {
        approvalValidationService.validate(request);

        Optional<PaymentTransaction> existingTransaction =
                idempotencyService.findExistingTransaction(request.getMerchantTransactionId());
        if (existingTransaction.isPresent()) {
            return approvalMapper.toMerchantApprovalResponse(existingTransaction.get());
        }

        String pgTransactionId = generatePgTransactionId();
        PaymentTransaction transaction = transactionLedgerService.createPendingTransaction(request, pgTransactionId);
        CardAuthorizationRequest cardRequest = cardAuthorizationRequestFactory.create(request, pgTransactionId);

        try {
            CardAuthorizationResponse cardResponse = cardAuthorizationClient.authorize(cardRequest);
            PaymentTransaction updatedTransaction = cardResponse.isApproved()
                    ? transactionLedgerService.markApproved(transaction, cardResponse)
                    : transactionLedgerService.markFailed(transaction, cardResponse);
            return approvalMapper.toMerchantApprovalResponse(updatedTransaction);
        } catch (Exception e) {
            PaymentTransaction timedOutTransaction =
                    transactionLedgerService.markTimeout(transaction, "PG approval processing failed");
            return approvalMapper.toMerchantApprovalResponse(timedOutTransaction);
        }
    }

    String generatePgTransactionId() {
        String timestamp = LocalDateTime.now().format(PG_TX_TIMESTAMP);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "PG" + timestamp + suffix;
    }
}
