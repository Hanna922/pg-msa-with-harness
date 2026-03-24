package dev.pg.routing.policy;

import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.routing.config.RoutingProperties;
import dev.pg.routing.model.AcquirerType;
import dev.pg.routing.model.CardBrand;
import dev.pg.routing.model.RoutingTarget;
import dev.pg.routing.service.BinResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@ConditionalOnProperty(name = "pg.routing.policy", havingValue = "bin", matchIfMissing = true)
public class BinBasedRoutingPolicy implements RoutingPolicy {

    private final BinResolver binResolver;
    private final RoutingProperties routingProperties;

    public BinBasedRoutingPolicy(BinResolver binResolver, RoutingProperties routingProperties) {
        this.binResolver = binResolver;
        this.routingProperties = routingProperties;
    }

    @Override
    public RoutingTarget route(MerchantApprovalRequest request) {
        CardBrand cardBrand = binResolver.resolve(request.getCardNumber());
        AcquirerType acquirerType = routingProperties.getBrandAcquirerMap()
                .getOrDefault(cardBrand, routingProperties.getDefaultAcquirer());

        return switch (acquirerType) {
            case CARD_AUTHORIZATION_SERVICE -> RoutingTarget.cardAuthorizationService();
            case CARD_AUTHORIZATION_SERVICE_2 -> RoutingTarget.cardAuthorizationService2();
        };
    }
}
