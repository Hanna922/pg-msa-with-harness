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
import dev.pg.ledger.enums.ApprovalStatus;
import dev.pg.ledger.enums.SettlementStatus;
import dev.pg.ledger.service.IdempotencyService;
import dev.pg.ledger.service.TransactionLedgerService;
import dev.pg.routing.model.AcquirerType;
import dev.pg.routing.model.RoutingTarget;
import dev.pg.routing.service.AcquirerRoutingService;
import dev.pg.support.exception.BusinessException;
import dev.pg.support.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentApprovalFacadeTest {

    private final AcquirerRoutingService acquirerRoutingService = mock(AcquirerRoutingService.class);
    private final ApprovalMapper approvalMapper = mock(ApprovalMapper.class);
    private final CardAuthorizationRequestFactory cardAuthorizationRequestFactory = mock(CardAuthorizationRequestFactory.class);
    private final ApprovalValidationService approvalValidationService = mock(ApprovalValidationService.class);
    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final TransactionLedgerService transactionLedgerService = mock(TransactionLedgerService.class);
    private final PaymentTransactionIdGenerator paymentTransactionIdGenerator = mock(PaymentTransactionIdGenerator.class);
    private final PaymentApprovalFacade facade =
            new PaymentApprovalFacade(
                    acquirerRoutingService,
                    approvalMapper,
                    cardAuthorizationRequestFactory,
                    approvalValidationService,
                    idempotencyService,
                    transactionLedgerService,
                    paymentTransactionIdGenerator
            );

    @Test
    void shouldPersistAndMapApprovedResponse() {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .mti("0100")
                .processingCode("000000")
                .transmissionDateTime("20260319153000")
                .stan("123456")
                .build();

        PaymentTransaction pendingTransaction = PaymentTransaction.builder()
                .pgTransactionId("PG202603190001ABCDEF")
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .acquirerType(AcquirerType.CARD_AUTHORIZATION_SERVICE)
                .approvalStatus(ApprovalStatus.PENDING)
                .settlementStatus(SettlementStatus.NOT_READY)
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 0))
                .build();
        PaymentTransaction approvedTransaction = PaymentTransaction.builder()
                .pgTransactionId("PG202603190001ABCDEF")
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .acquirerType(AcquirerType.CARD_AUTHORIZATION_SERVICE)
                .approvalStatus(ApprovalStatus.APPROVED)
                .settlementStatus(SettlementStatus.READY)
                .responseCode("00")
                .message("Approved")
                .approvalNumber("12345678")
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 0))
                .respondedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .approvedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .build();
        CardAuthorizationRequest cardAuthorizationRequest = CardAuthorizationRequest.builder()
                .transactionId("PG202603190001ABCDEF")
                .cardNumber("4111111111111111")
                .amount(new BigDecimal("10000"))
                .merchantId("MERCHANT-001")
                .terminalId(null)
                .pin(null)
                .build();

        when(idempotencyService.findExistingTransaction("M202603190001")).thenReturn(Optional.empty());
        when(paymentTransactionIdGenerator.generate()).thenReturn("PG202603190001ABCDEF");
        when(acquirerRoutingService.resolveRoutingTarget(request)).thenReturn(RoutingTarget.cardAuthorizationService());
        when(transactionLedgerService.createPendingTransaction(
                request,
                "PG202603190001ABCDEF",
                AcquirerType.CARD_AUTHORIZATION_SERVICE
        ))
                .thenReturn(pendingTransaction);
        when(transactionLedgerService.markApproved(any(), any())).thenReturn(approvedTransaction);
        when(cardAuthorizationRequestFactory.create(request, "PG202603190001ABCDEF"))
                .thenReturn(cardAuthorizationRequest);
        when(approvalMapper.toMerchantApprovalResponse(approvedTransaction)).thenReturn(
                MerchantApprovalResponse.builder()
                        .merchantTransactionId("M202603190001")
                        .pgTransactionId("PG202603190001ABCDEF")
                        .approved(true)
                        .responseCode("00")
                        .message("Approved")
                        .approvalNumber("12345678")
                        .approvedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                        .build()
        );
        when(acquirerRoutingService.authorize(RoutingTarget.cardAuthorizationService(), cardAuthorizationRequest)).thenReturn(CardAuthorizationResponse.builder()
                .transactionId("PG202603190001ABCDEF")
                .approvalNumber("12345678")
                .responseCode("00")
                .message("Approved")
                .amount(new BigDecimal("10000"))
                .authorizationDate(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .approved(true)
                .build());

        MerchantApprovalResponse response = facade.approve(request);

        assertEquals("M202603190001", response.getMerchantTransactionId());
        assertEquals("PG202603190001ABCDEF", response.getPgTransactionId());
        assertTrue(response.isApproved());
        assertEquals("00", response.getResponseCode());
        assertEquals("12345678", response.getApprovalNumber());
        assertEquals(LocalDateTime.of(2026, 3, 19, 15, 30, 5), response.getApprovedAt());
    }

    @Test
    void shouldCreatePendingTransactionWithSecondAcquirerForMastercard() {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603190002")
                .merchantId("MERCHANT-001")
                .cardNumber("5555555555554444")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .mti("0100")
                .processingCode("000000")
                .transmissionDateTime("20260319153100")
                .stan("123457")
                .build();

        PaymentTransaction pendingTransaction = PaymentTransaction.builder()
                .pgTransactionId("PG202603190002ABCDEF")
                .merchantTransactionId("M202603190002")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("555555******4444")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .acquirerType(AcquirerType.CARD_AUTHORIZATION_SERVICE_2)
                .approvalStatus(ApprovalStatus.PENDING)
                .settlementStatus(SettlementStatus.NOT_READY)
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 31, 0))
                .build();
        PaymentTransaction approvedTransaction = PaymentTransaction.builder()
                .pgTransactionId("PG202603190002ABCDEF")
                .merchantTransactionId("M202603190002")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("555555******4444")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .acquirerType(AcquirerType.CARD_AUTHORIZATION_SERVICE_2)
                .approvalStatus(ApprovalStatus.APPROVED)
                .settlementStatus(SettlementStatus.READY)
                .responseCode("00")
                .message("Approved")
                .approvalNumber("87654321")
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 31, 0))
                .respondedAt(LocalDateTime.of(2026, 3, 19, 15, 31, 5))
                .approvedAt(LocalDateTime.of(2026, 3, 19, 15, 31, 5))
                .build();
        CardAuthorizationRequest cardAuthorizationRequest = CardAuthorizationRequest.builder()
                .transactionId("PG202603190002ABCDEF")
                .cardNumber("5555555555554444")
                .amount(new BigDecimal("10000"))
                .merchantId("MERCHANT-001")
                .build();

        when(idempotencyService.findExistingTransaction("M202603190002")).thenReturn(Optional.empty());
        when(paymentTransactionIdGenerator.generate()).thenReturn("PG202603190002ABCDEF");
        when(acquirerRoutingService.resolveRoutingTarget(request)).thenReturn(RoutingTarget.cardAuthorizationService2());
        when(transactionLedgerService.createPendingTransaction(
                request,
                "PG202603190002ABCDEF",
                AcquirerType.CARD_AUTHORIZATION_SERVICE_2
        )).thenReturn(pendingTransaction);
        when(cardAuthorizationRequestFactory.create(request, "PG202603190002ABCDEF"))
                .thenReturn(cardAuthorizationRequest);
        when(acquirerRoutingService.authorize(RoutingTarget.cardAuthorizationService2(), cardAuthorizationRequest))
                .thenReturn(CardAuthorizationResponse.builder()
                        .transactionId("PG202603190002ABCDEF")
                        .approvalNumber("87654321")
                        .responseCode("00")
                        .message("Approved")
                        .amount(new BigDecimal("10000"))
                        .authorizationDate(LocalDateTime.of(2026, 3, 19, 15, 31, 5))
                        .approved(true)
                        .build());
        when(transactionLedgerService.markApproved(eq(pendingTransaction), org.mockito.ArgumentMatchers.any()))
                .thenReturn(approvedTransaction);
        when(approvalMapper.toMerchantApprovalResponse(approvedTransaction)).thenReturn(
                MerchantApprovalResponse.builder()
                        .merchantTransactionId("M202603190002")
                        .pgTransactionId("PG202603190002ABCDEF")
                        .approved(true)
                        .responseCode("00")
                        .message("Approved")
                        .approvalNumber("87654321")
                        .approvedAt(LocalDateTime.of(2026, 3, 19, 15, 31, 5))
                        .build()
        );

        MerchantApprovalResponse response = facade.approve(request);

        assertTrue(response.isApproved());
        verify(transactionLedgerService).createPendingTransaction(
                request,
                "PG202603190002ABCDEF",
                AcquirerType.CARD_AUTHORIZATION_SERVICE_2
        );
        verify(acquirerRoutingService).authorize(RoutingTarget.cardAuthorizationService2(), cardAuthorizationRequest);
    }

    @Test
    void shouldReturnExistingTransactionForDuplicateMerchantTransactionId() {
        PaymentTransaction existingTransaction = PaymentTransaction.builder()
                .pgTransactionId("PG202603190001ABCDEF")
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .acquirerType(AcquirerType.CARD_AUTHORIZATION_SERVICE)
                .approvalStatus(ApprovalStatus.APPROVED)
                .settlementStatus(SettlementStatus.READY)
                .responseCode("00")
                .message("Approved")
                .approvalNumber("12345678")
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 0))
                .respondedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .approvedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .build();
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();

        when(idempotencyService.findExistingTransaction("M202603190001"))
                .thenReturn(Optional.of(existingTransaction));
        when(approvalMapper.toMerchantApprovalResponse(existingTransaction)).thenReturn(
                MerchantApprovalResponse.builder()
                        .merchantTransactionId("M202603190001")
                        .pgTransactionId("PG202603190001ABCDEF")
                        .approved(true)
                        .responseCode("00")
                        .message("Approved")
                        .approvalNumber("12345678")
                        .approvedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                        .build()
        );

        MerchantApprovalResponse response = facade.approve(request);

        assertEquals("PG202603190001ABCDEF", response.getPgTransactionId());
        assertTrue(response.isApproved());
        verifyNoInteractions(acquirerRoutingService, transactionLedgerService, paymentTransactionIdGenerator);
    }

    @Test
    void shouldValidateRequiredFields() {
        MerchantApprovalRequest invalidRequest = MerchantApprovalRequest.builder().build();

        org.mockito.Mockito.doThrow(new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "merchantTransactionId is required"
        ))
                .when(approvalValidationService).validate(invalidRequest);

        BusinessException exception = assertThrows(BusinessException.class, () -> facade.approve(invalidRequest));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("merchantTransactionId is required", exception.getMessage());
    }

    @Test
    void shouldMarkTimeoutForCommunicationFailure() {
        MerchantApprovalRequest request = createRequest();
        PaymentTransaction pendingTransaction = createPendingTransaction();
        PaymentTransaction timeoutTransaction = PaymentTransaction.builder()
                .pgTransactionId("PG202603190001ABCDEF")
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .acquirerType(AcquirerType.CARD_AUTHORIZATION_SERVICE)
                .approvalStatus(ApprovalStatus.TIMEOUT)
                .settlementStatus(SettlementStatus.NOT_READY)
                .responseCode("96")
                .message("Card authorization service communication failed")
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 0))
                .respondedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .build();
        CardAuthorizationRequest cardAuthorizationRequest = createCardAuthorizationRequest();
        MerchantApprovalResponse mappedResponse = MerchantApprovalResponse.builder()
                .merchantTransactionId("M202603190001")
                .pgTransactionId("PG202603190001ABCDEF")
                .approved(false)
                .responseCode("96")
                .message("Card authorization service communication failed")
                .build();

        when(idempotencyService.findExistingTransaction("M202603190001")).thenReturn(Optional.empty());
        when(paymentTransactionIdGenerator.generate()).thenReturn("PG202603190001ABCDEF");
        when(acquirerRoutingService.resolveRoutingTarget(request)).thenReturn(RoutingTarget.cardAuthorizationService());
        when(transactionLedgerService.createPendingTransaction(
                request,
                "PG202603190001ABCDEF",
                AcquirerType.CARD_AUTHORIZATION_SERVICE
        ))
                .thenReturn(pendingTransaction);
        when(cardAuthorizationRequestFactory.create(request, "PG202603190001ABCDEF"))
                .thenReturn(cardAuthorizationRequest);
        when(acquirerRoutingService.authorize(RoutingTarget.cardAuthorizationService(), cardAuthorizationRequest)).thenThrow(new CardAuthorizationClientException(
                CardAuthorizationErrorType.COMMUNICATION_FAILURE,
                "Card authorization service communication failed"
        ));
        when(transactionLedgerService.markTimeout(pendingTransaction, "Card authorization service communication failed"))
                .thenReturn(timeoutTransaction);
        when(approvalMapper.toMerchantApprovalResponse(timeoutTransaction)).thenReturn(mappedResponse);

        MerchantApprovalResponse response = facade.approve(request);

        assertEquals("96", response.getResponseCode());
        assertEquals("Card authorization service communication failed", response.getMessage());
        verify(transactionLedgerService).markTimeout(pendingTransaction, "Card authorization service communication failed");
    }

    @Test
    void shouldMarkFailedForDownstreamFailure() {
        MerchantApprovalRequest request = createRequest();
        PaymentTransaction pendingTransaction = createPendingTransaction();
        PaymentTransaction failedTransaction = PaymentTransaction.builder()
                .pgTransactionId("PG202603190001ABCDEF")
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .acquirerType(AcquirerType.CARD_AUTHORIZATION_SERVICE)
                .approvalStatus(ApprovalStatus.FAILED)
                .settlementStatus(SettlementStatus.NOT_READY)
                .responseCode("96")
                .message("Card authorization service returned HTTP 503")
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 0))
                .respondedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .build();
        CardAuthorizationRequest cardAuthorizationRequest = createCardAuthorizationRequest();
        MerchantApprovalResponse mappedResponse = MerchantApprovalResponse.builder()
                .merchantTransactionId("M202603190001")
                .pgTransactionId("PG202603190001ABCDEF")
                .approved(false)
                .responseCode("96")
                .message("Card authorization service returned HTTP 503")
                .build();

        when(idempotencyService.findExistingTransaction("M202603190001")).thenReturn(Optional.empty());
        when(paymentTransactionIdGenerator.generate()).thenReturn("PG202603190001ABCDEF");
        when(acquirerRoutingService.resolveRoutingTarget(request)).thenReturn(RoutingTarget.cardAuthorizationService());
        when(transactionLedgerService.createPendingTransaction(
                request,
                "PG202603190001ABCDEF",
                AcquirerType.CARD_AUTHORIZATION_SERVICE
        ))
                .thenReturn(pendingTransaction);
        when(cardAuthorizationRequestFactory.create(request, "PG202603190001ABCDEF"))
                .thenReturn(cardAuthorizationRequest);
        when(acquirerRoutingService.authorize(RoutingTarget.cardAuthorizationService(), cardAuthorizationRequest)).thenThrow(new CardAuthorizationClientException(
                CardAuthorizationErrorType.DOWNSTREAM_FAILURE,
                "Card authorization service returned HTTP 503"
        ));
        when(transactionLedgerService.markFailed(
                eq(pendingTransaction),
                eq("96"),
                eq("Card authorization service returned HTTP 503")
        )).thenReturn(failedTransaction);
        when(approvalMapper.toMerchantApprovalResponse(failedTransaction)).thenReturn(mappedResponse);

        MerchantApprovalResponse response = facade.approve(request);

        assertEquals("96", response.getResponseCode());
        assertEquals("Card authorization service returned HTTP 503", response.getMessage());
        verify(transactionLedgerService).markFailed(
                pendingTransaction,
                "96",
                "Card authorization service returned HTTP 503"
        );
    }

    @Test
    void shouldMarkFailedForCircuitOpen() {
        MerchantApprovalRequest request = createRequest();
        PaymentTransaction pendingTransaction = createPendingTransaction();
        PaymentTransaction failedTransaction = PaymentTransaction.builder()
                .pgTransactionId("PG202603190001ABCDEF")
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .acquirerType(AcquirerType.CARD_AUTHORIZATION_SERVICE)
                .approvalStatus(ApprovalStatus.FAILED)
                .settlementStatus(SettlementStatus.NOT_READY)
                .responseCode("96")
                .message("Card authorization circuit breaker is open")
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 0))
                .respondedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 5))
                .build();
        CardAuthorizationRequest cardAuthorizationRequest = createCardAuthorizationRequest();
        MerchantApprovalResponse mappedResponse = MerchantApprovalResponse.builder()
                .merchantTransactionId("M202603190001")
                .pgTransactionId("PG202603190001ABCDEF")
                .approved(false)
                .responseCode("96")
                .message("Card authorization circuit breaker is open")
                .build();

        when(idempotencyService.findExistingTransaction("M202603190001")).thenReturn(Optional.empty());
        when(paymentTransactionIdGenerator.generate()).thenReturn("PG202603190001ABCDEF");
        when(acquirerRoutingService.resolveRoutingTarget(request)).thenReturn(RoutingTarget.cardAuthorizationService());
        when(transactionLedgerService.createPendingTransaction(
                request,
                "PG202603190001ABCDEF",
                AcquirerType.CARD_AUTHORIZATION_SERVICE
        ))
                .thenReturn(pendingTransaction);
        when(cardAuthorizationRequestFactory.create(request, "PG202603190001ABCDEF"))
                .thenReturn(cardAuthorizationRequest);
        when(acquirerRoutingService.authorize(RoutingTarget.cardAuthorizationService(), cardAuthorizationRequest)).thenThrow(new CardAuthorizationClientException(
                CardAuthorizationErrorType.CIRCUIT_OPEN,
                "Card authorization circuit breaker is open"
        ));
        when(transactionLedgerService.markFailed(
                eq(pendingTransaction),
                eq("96"),
                eq("Card authorization circuit breaker is open")
        )).thenReturn(failedTransaction);
        when(approvalMapper.toMerchantApprovalResponse(failedTransaction)).thenReturn(mappedResponse);

        MerchantApprovalResponse response = facade.approve(request);

        assertEquals("96", response.getResponseCode());
        assertEquals("Card authorization circuit breaker is open", response.getMessage());
        verify(transactionLedgerService).markFailed(
                pendingTransaction,
                "96",
                "Card authorization circuit breaker is open"
        );
    }

    private MerchantApprovalRequest createRequest() {
        return MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .mti("0100")
                .processingCode("000000")
                .transmissionDateTime("20260319153000")
                .stan("123456")
                .build();
    }

    private PaymentTransaction createPendingTransaction() {
        return PaymentTransaction.builder()
                .pgTransactionId("PG202603190001ABCDEF")
                .merchantTransactionId("M202603190001")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .acquirerType(AcquirerType.CARD_AUTHORIZATION_SERVICE)
                .approvalStatus(ApprovalStatus.PENDING)
                .settlementStatus(SettlementStatus.NOT_READY)
                .requestedAt(LocalDateTime.of(2026, 3, 19, 15, 30, 0))
                .build();
    }

    private CardAuthorizationRequest createCardAuthorizationRequest() {
        return CardAuthorizationRequest.builder()
                .transactionId("PG202603190001ABCDEF")
                .cardNumber("4111111111111111")
                .amount(new BigDecimal("10000"))
                .merchantId("MERCHANT-001")
                .terminalId(null)
                .pin(null)
                .build();
    }
}
