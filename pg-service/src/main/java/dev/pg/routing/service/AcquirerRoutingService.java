package dev.pg.routing.service;

import dev.pg.client.CardAuthorizationClient;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.routing.model.AcquirerType;
import dev.pg.routing.model.RoutingTarget;
import dev.pg.routing.policy.RoutingPolicy;
import dev.pg.support.exception.BusinessException;
import dev.pg.support.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class AcquirerRoutingService {

    private final RoutingPolicy routingPolicy;
    private final CardAuthorizationClient cardAuthorizationClient;

    public AcquirerRoutingService(
            RoutingPolicy routingPolicy,
            CardAuthorizationClient cardAuthorizationClient
    ) {
        this.routingPolicy = routingPolicy;
        this.cardAuthorizationClient = cardAuthorizationClient;
    }

    public CardAuthorizationResponse authorize(
            MerchantApprovalRequest merchantRequest,
            CardAuthorizationRequest authorizationRequest
    ) {
        RoutingTarget routingTarget = routingPolicy.route(merchantRequest);

        if (routingTarget.acquirerType() == AcquirerType.CARD_AUTHORIZATION_SERVICE) {
            return cardAuthorizationClient.authorize(authorizationRequest);
        }

        throw new BusinessException(
                ErrorCode.INTERNAL_ERROR,
                "Unsupported routing target: " + routingTarget.acquirerType()
        );
    }
}
