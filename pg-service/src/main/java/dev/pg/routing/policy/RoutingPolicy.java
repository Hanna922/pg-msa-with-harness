package dev.pg.routing.policy;

import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.routing.model.RoutingTarget;

public interface RoutingPolicy {

    RoutingTarget route(MerchantApprovalRequest request);
}
