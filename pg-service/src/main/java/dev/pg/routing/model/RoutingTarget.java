package dev.pg.routing.model;

public record RoutingTarget(
        AcquirerType acquirerType,
        String serviceName
) {

    public static RoutingTarget cardAuthorizationService() {
        return new RoutingTarget(
                AcquirerType.CARD_AUTHORIZATION_SERVICE,
                "card-authorization-service"
        );
    }

    public static RoutingTarget cardAuthorizationService2() {
        return new RoutingTarget(
                AcquirerType.CARD_AUTHORIZATION_SERVICE_2,
                "card-authorization-service-2"
        );
    }
}
