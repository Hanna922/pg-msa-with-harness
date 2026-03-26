package dev.pg.routing.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingTargetTest {

    @Test
    void shouldCreateDefaultCardAuthorizationRoutingTarget() {
        RoutingTarget target = RoutingTarget.cardAuthorizationService();

        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE, target.acquirerType());
        assertEquals("card-authorization-service", target.serviceName());
    }

    @Test
    void shouldCreateSecondCardAuthorizationRoutingTarget() {
        RoutingTarget target = RoutingTarget.cardAuthorizationService2();

        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE_2, target.acquirerType());
        assertEquals("card-authorization-service-2", target.serviceName());
    }
}
