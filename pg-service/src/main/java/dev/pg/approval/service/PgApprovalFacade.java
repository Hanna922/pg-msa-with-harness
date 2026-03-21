package dev.pg.approval.service;

import dev.pg.approval.mapper.ApprovalMapper;
import dev.pg.approval.mapper.CardAuthorizationRequestFactory;
import dev.pg.client.support.CardAuthorizationClientException;
import dev.pg.client.support.CardAuthorizationErrorType;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import dev.pg.ledger.entity.PaymentTransaction;
import dev.pg.ledger.service.IdempotencyService;
import dev.pg.ledger.service.TransactionLedgerService;
import dev.pg.routing.service.AcquirerRoutingService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PgApprovalFacade {

    private final AcquirerRoutingService acquirerRoutingService;
    private final ApprovalMapper approvalMapper;
    private final CardAuthorizationRequestFactory cardAuthorizationRequestFactory;
    private final ApprovalValidationService approvalValidationService;
    private final IdempotencyService idempotencyService;
    private final TransactionLedgerService transactionLedgerService;
    private final PgTransactionIdGenerator pgTransactionIdGenerator;

    public PgApprovalFacade(
            AcquirerRoutingService acquirerRoutingService,
            ApprovalMapper approvalMapper,
            CardAuthorizationRequestFactory cardAuthorizationRequestFactory,
            ApprovalValidationService approvalValidationService,
            IdempotencyService idempotencyService,
            TransactionLedgerService transactionLedgerService,
            PgTransactionIdGenerator pgTransactionIdGenerator
    ) {
        this.acquirerRoutingService = acquirerRoutingService;
        this.approvalMapper = approvalMapper;
        this.cardAuthorizationRequestFactory = cardAuthorizationRequestFactory;
        this.approvalValidationService = approvalValidationService;
        this.idempotencyService = idempotencyService;
        this.transactionLedgerService = transactionLedgerService;
        this.pgTransactionIdGenerator = pgTransactionIdGenerator;
    }

    public MerchantApprovalResponse approve(MerchantApprovalRequest request) {
        approvalValidationService.validate(request);

        Optional<PaymentTransaction> existingTransaction =
                idempotencyService.findExistingTransaction(request.getMerchantTransactionId());
        if (existingTransaction.isPresent()) {
            return approvalMapper.toMerchantApprovalResponse(existingTransaction.get());
        }

        String pgTransactionId = pgTransactionIdGenerator.generate();
        PaymentTransaction transaction = transactionLedgerService.createPendingTransaction(request, pgTransactionId);
        CardAuthorizationRequest cardRequest = cardAuthorizationRequestFactory.create(request, pgTransactionId);

        try {
            CardAuthorizationResponse cardResponse = acquirerRoutingService.authorize(request, cardRequest);
            PaymentTransaction updatedTransaction = cardResponse.isApproved()
                    ? transactionLedgerService.markApproved(transaction, cardResponse)
                    : transactionLedgerService.markFailed(transaction, cardResponse);
            return approvalMapper.toMerchantApprovalResponse(updatedTransaction);
        } catch (CardAuthorizationClientException e) {
            PaymentTransaction failedTransaction = handleClientFailure(transaction, e);
            return approvalMapper.toMerchantApprovalResponse(failedTransaction);
        }
    }

    private PaymentTransaction handleClientFailure(
            PaymentTransaction transaction,
            CardAuthorizationClientException exception
    ) {
        if (exception.getErrorType() == CardAuthorizationErrorType.COMMUNICATION_FAILURE) {
            return transactionLedgerService.markTimeout(transaction, exception.getMessage());
        }

        return transactionLedgerService.markFailed(transaction, "96", exception.getMessage());
    }
}
