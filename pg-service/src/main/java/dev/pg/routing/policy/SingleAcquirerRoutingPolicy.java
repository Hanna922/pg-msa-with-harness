package dev.pg.routing.policy;

import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.routing.model.RoutingTarget;
import org.springframework.stereotype.Component;

@Component
public class SingleAcquirerRoutingPolicy implements RoutingPolicy {

    @Override
    public RoutingTarget route(MerchantApprovalRequest request) {
        return RoutingTarget.cardAuthorizationService();
    }
}
