package dev.pg.routing.policy;

import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.routing.model.RoutingTarget;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pg.routing.policy", havingValue = "single")
public class SingleAcquirerRoutingPolicy implements RoutingPolicy {

    @Override
    public RoutingTarget route(MerchantApprovalRequest request) {
        return RoutingTarget.cardAuthorizationService();
    }
}
