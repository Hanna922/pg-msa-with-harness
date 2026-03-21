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
}
