package dev.pg.routing.service;

import dev.pg.client.CardAuthorizationClient;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.routing.model.RoutingTarget;
import dev.pg.routing.policy.RoutingPolicy;
import dev.pg.support.exception.BusinessException;
import dev.pg.support.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcquirerRoutingServiceTest {

    private final RoutingPolicy routingPolicy = mock(RoutingPolicy.class);
    private final CardAuthorizationClient cardAuthorizationClient = mock(CardAuthorizationClient.class);
    private final AcquirerRoutingService acquirerRoutingService =
            new AcquirerRoutingService(routingPolicy, cardAuthorizationClient);

    @Test
    void shouldAuthorizeThroughCardAuthorizationClient() {
        MerchantApprovalRequest merchantRequest = createMerchantRequest();
        CardAuthorizationRequest authorizationRequest = createAuthorizationRequest();
        CardAuthorizationResponse response = CardAuthorizationResponse.builder()
                .transactionId("PG202603210001ABCDEF")
                .responseCode("00")
                .message("Approved")
                .approved(true)
                .build();

        when(routingPolicy.route(merchantRequest)).thenReturn(RoutingTarget.cardAuthorizationService());
        when(cardAuthorizationClient.authorize(authorizationRequest)).thenReturn(response);

        CardAuthorizationResponse actual = acquirerRoutingService.authorize(merchantRequest, authorizationRequest);

        assertEquals("00", actual.getResponseCode());
        verify(routingPolicy).route(merchantRequest);
        verify(cardAuthorizationClient).authorize(authorizationRequest);
    }

    @Test
    void shouldThrowBusinessExceptionForUnsupportedTarget() {
        MerchantApprovalRequest merchantRequest = createMerchantRequest();
        CardAuthorizationRequest authorizationRequest = createAuthorizationRequest();

        when(routingPolicy.route(merchantRequest)).thenReturn(new RoutingTarget(null, "unknown"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> acquirerRoutingService.authorize(merchantRequest, authorizationRequest)
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("Unsupported routing target: null", exception.getMessage());
        verify(routingPolicy).route(merchantRequest);
        verify(cardAuthorizationClient, never()).authorize(authorizationRequest);
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
