package dev.pg.routing.policy;

import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.routing.model.AcquirerType;
import dev.pg.routing.model.RoutingTarget;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingleAcquirerRoutingPolicyTest {

    private final SingleAcquirerRoutingPolicy routingPolicy = new SingleAcquirerRoutingPolicy();

    @Test
    void shouldAlwaysRouteToDefaultCardAuthorizationService() {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603210001")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();

        RoutingTarget routingTarget = routingPolicy.route(request);

        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE, routingTarget.acquirerType());
        assertEquals("card-authorization-service", routingTarget.serviceName());
    }
}
