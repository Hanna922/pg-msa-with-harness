package dev.pg.routing.policy;

import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.routing.model.RoutingTarget;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingPolicyTest {

    @Test
    void shouldReturnRoutingTargetFromPolicyContract() {
        RoutingPolicy policy = request -> RoutingTarget.cardAuthorizationService();
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603210001")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();

        RoutingTarget target = policy.route(request);

        assertEquals("card-authorization-service", target.serviceName());
    }
}
