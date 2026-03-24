package dev.pg.routing.service;

import dev.pg.client.AcquirerClient;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.routing.model.AcquirerType;
import dev.pg.routing.model.RoutingTarget;
import dev.pg.routing.policy.RoutingPolicy;
import dev.pg.support.exception.BusinessException;
import dev.pg.support.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcquirerRoutingServiceTest {

    private final RoutingPolicy routingPolicy = mock(RoutingPolicy.class);
    private final AcquirerClient acquirerClientA = mock(AcquirerClient.class);
    private final AcquirerClient acquirerClientB = mock(AcquirerClient.class);
    private final AcquirerRoutingService acquirerRoutingService;

    AcquirerRoutingServiceTest() {
        when(acquirerClientA.getAcquirerType()).thenReturn(AcquirerType.CARD_AUTHORIZATION_SERVICE);
        when(acquirerClientB.getAcquirerType()).thenReturn(AcquirerType.CARD_AUTHORIZATION_SERVICE_2);
        acquirerRoutingService = new AcquirerRoutingService(routingPolicy, List.of(acquirerClientA, acquirerClientB));
    }

    @Test
    void shouldAuthorizeThroughCardAuthorizationClient() {
        MerchantApprovalRequest merchantRequest = createMerchantRequest();
        CardAuthorizationRequest authorizationRequest = createAuthorizationRequest();
        RoutingTarget routingTarget = RoutingTarget.cardAuthorizationService();
        CardAuthorizationResponse response = CardAuthorizationResponse.builder()
                .transactionId("PG202603210001ABCDEF")
                .responseCode("00")
                .message("Approved")
                .approved(true)
                .build();

        when(acquirerClientA.authorize(authorizationRequest)).thenReturn(response);

        CardAuthorizationResponse actual = acquirerRoutingService.authorize(routingTarget, authorizationRequest);

        assertEquals("00", actual.getResponseCode());
        verify(acquirerClientA).authorize(authorizationRequest);
        verify(acquirerClientB, never()).authorize(authorizationRequest);
    }

    @Test
    void shouldAuthorizeThroughSecondAcquirerClient() {
        MerchantApprovalRequest merchantRequest = createMerchantRequest();
        CardAuthorizationRequest authorizationRequest = createAuthorizationRequest();
        RoutingTarget routingTarget = RoutingTarget.cardAuthorizationService2();
        CardAuthorizationResponse response = CardAuthorizationResponse.builder()
                .transactionId("PG202603210001ABCDEF")
                .responseCode("00")
                .message("Approved by second acquirer")
                .approved(true)
                .build();

        when(acquirerClientB.authorize(authorizationRequest)).thenReturn(response);

        CardAuthorizationResponse actual = acquirerRoutingService.authorize(routingTarget, authorizationRequest);

        assertEquals("00", actual.getResponseCode());
        assertEquals("Approved by second acquirer", actual.getMessage());
        verify(acquirerClientB).authorize(authorizationRequest);
        verify(acquirerClientA, never()).authorize(authorizationRequest);
    }

    @Test
    void shouldThrowBusinessExceptionForUnsupportedTarget() {
        CardAuthorizationRequest authorizationRequest = createAuthorizationRequest();
        RoutingTarget routingTarget = new RoutingTarget(null, "unknown");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> acquirerRoutingService.authorize(routingTarget, authorizationRequest)
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("Unsupported routing target: null", exception.getMessage());
        verify(acquirerClientA, never()).authorize(authorizationRequest);
        verify(acquirerClientB, never()).authorize(authorizationRequest);
    }

    @Test
    void shouldResolveRoutingTargetThroughPolicy() {
        MerchantApprovalRequest merchantRequest = createMerchantRequest();

        when(routingPolicy.route(merchantRequest)).thenReturn(RoutingTarget.cardAuthorizationService2());

        RoutingTarget actual = acquirerRoutingService.resolveRoutingTarget(merchantRequest);

        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE_2, actual.acquirerType());
        verify(routingPolicy).route(merchantRequest);
    }

    private MerchantApprovalRequest createMerchantRequest() {
        return MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603210001")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();
    }

    private CardAuthorizationRequest createAuthorizationRequest() {
        return CardAuthorizationRequest.builder()
                .transactionId("PG202603210001ABCDEF")
                .cardNumber("4111111111111111")
                .amount(new BigDecimal("10000"))
                .merchantId("MERCHANT-001")
                .build();
    }
}
