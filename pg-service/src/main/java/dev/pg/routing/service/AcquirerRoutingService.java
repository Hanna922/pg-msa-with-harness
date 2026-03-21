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
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class AcquirerRoutingService {

    private final RoutingPolicy routingPolicy;
    private final Map<AcquirerType, AcquirerClient> acquirerClients;

    public AcquirerRoutingService(
            RoutingPolicy routingPolicy,
            List<AcquirerClient> acquirerClients
    ) {
        this.routingPolicy = routingPolicy;
        this.acquirerClients = new EnumMap<>(AcquirerType.class);
        for (AcquirerClient acquirerClient : acquirerClients) {
            this.acquirerClients.put(acquirerClient.getAcquirerType(), acquirerClient);
        }
    }

    public CardAuthorizationResponse authorize(
            MerchantApprovalRequest merchantRequest,
            CardAuthorizationRequest authorizationRequest
    ) {
        RoutingTarget routingTarget = routingPolicy.route(merchantRequest);
        AcquirerClient acquirerClient = acquirerClients.get(routingTarget.acquirerType());

        if (acquirerClient != null) {
            return acquirerClient.authorize(authorizationRequest);
        }

        throw new BusinessException(
                ErrorCode.INTERNAL_ERROR,
                "Unsupported routing target: " + routingTarget.acquirerType()
        );
    }
}
