package dev.pg.approval.service;

import dev.pg.approval.mapper.ApprovalMapper;
import dev.pg.approval.mapper.CardAuthorizationRequestFactory;
import dev.pg.client.LedgerClient;
import dev.pg.client.dto.CreateLedgerTransactionRequest;
import dev.pg.client.support.CardAuthorizationClientException;
import dev.pg.client.support.CardAuthorizationErrorType;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import dev.pg.ledger.entity.PaymentTransaction;
import dev.pg.ledger.service.IdempotencyService;
import dev.pg.ledger.service.TransactionLedgerService;
import dev.pg.routing.model.RoutingTarget;
import dev.pg.routing.service.AcquirerRoutingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class PaymentApprovalFacade {

    private final AcquirerRoutingService acquirerRoutingService;
    private final ApprovalMapper approvalMapper;
    private final CardAuthorizationRequestFactory cardAuthorizationRequestFactory;
    private final ApprovalValidationService approvalValidationService;
    private final IdempotencyService idempotencyService;
    private final TransactionLedgerService transactionLedgerService;
    private final LedgerClient ledgerClient;
    private final PaymentTransactionIdGenerator paymentTransactionIdGenerator;

    public PaymentApprovalFacade(
            AcquirerRoutingService acquirerRoutingService,
            ApprovalMapper approvalMapper,
            CardAuthorizationRequestFactory cardAuthorizationRequestFactory,
            ApprovalValidationService approvalValidationService,
            IdempotencyService idempotencyService,
            TransactionLedgerService transactionLedgerService,
            LedgerClient ledgerClient,
            PaymentTransactionIdGenerator paymentTransactionIdGenerator
    ) {
        this.acquirerRoutingService = acquirerRoutingService;
        this.approvalMapper = approvalMapper;
        this.cardAuthorizationRequestFactory = cardAuthorizationRequestFactory;
        this.approvalValidationService = approvalValidationService;
        this.idempotencyService = idempotencyService;
        this.transactionLedgerService = transactionLedgerService;
        this.ledgerClient = ledgerClient;
        this.paymentTransactionIdGenerator = paymentTransactionIdGenerator;
    }

    public MerchantApprovalResponse approve(MerchantApprovalRequest request) {
        approvalValidationService.validate(request);

        Optional<PaymentTransaction> existingTransaction =
                idempotencyService.findExistingTransaction(request.getMerchantTransactionId());
        if (existingTransaction.isPresent()) {
            return approvalMapper.toMerchantApprovalResponse(existingTransaction.get());
        }

        String pgTransactionId = paymentTransactionIdGenerator.generate();
        RoutingTarget routingTarget = acquirerRoutingService.resolveRoutingTarget(request);
        PaymentTransaction transaction = transactionLedgerService.createPendingTransaction(
                request,
                pgTransactionId,
                routingTarget.acquirerType()
        );
        CardAuthorizationRequest cardRequest = cardAuthorizationRequestFactory.create(request, pgTransactionId);

        try {
            CardAuthorizationResponse cardResponse = acquirerRoutingService.authorize(routingTarget, cardRequest);
            PaymentTransaction updatedTransaction = cardResponse.isApproved()
                    ? transactionLedgerService.markApproved(transaction, cardResponse)
                    : transactionLedgerService.markFailed(transaction, cardResponse);
            syncLedgerTransaction(updatedTransaction);
            return approvalMapper.toMerchantApprovalResponse(updatedTransaction);
        } catch (CardAuthorizationClientException e) {
            PaymentTransaction failedTransaction = handleClientFailure(transaction, e);
            syncLedgerTransaction(failedTransaction);
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

    private void syncLedgerTransaction(PaymentTransaction transaction) {
        try {
            ledgerClient.syncTransaction(CreateLedgerTransactionRequest.builder()
                    .pgTransactionId(transaction.getPgTransactionId())
                    .merchantTransactionId(transaction.getMerchantTransactionId())
                    .merchantId(transaction.getMerchantId())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .approvalStatus(transaction.getApprovalStatus())
                    .settlementStatus(transaction.getSettlementStatus())
                    .acquirerType(transaction.getAcquirerType().name())
                    .responseCode(transaction.getResponseCode())
                    .message(transaction.getMessage())
                    .approvalNumber(transaction.getApprovalNumber())
                    .approvedAt(transaction.getApprovedAt())
                    .build());
        } catch (RuntimeException exception) {
            log.warn("Failed to sync transaction to ledger-service. pgTransactionId={}", transaction.getPgTransactionId(), exception);
        }
    }
}
