package dev.pg.routing.policy;

import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.routing.config.RoutingProperties;
import dev.pg.routing.model.AcquirerType;
import dev.pg.routing.model.CardBrand;
import dev.pg.routing.model.RoutingTarget;
import dev.pg.routing.service.BinResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BinBasedRoutingPolicyTest {

    private final BinResolver binResolver = new BinResolver();

    @Test
    void shouldRouteVisaToFirstAcquirer() {
        BinBasedRoutingPolicy policy = new BinBasedRoutingPolicy(binResolver, createRoutingProperties());

        RoutingTarget target = policy.route(createRequest("4111111111111111"));

        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE, target.acquirerType());
        assertEquals("card-authorization-service", target.serviceName());
    }

    @Test
    void shouldRouteMastercardToSecondAcquirer() {
        BinBasedRoutingPolicy policy = new BinBasedRoutingPolicy(binResolver, createRoutingProperties());

        RoutingTarget target = policy.route(createRequest("5555555555554444"));

        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE_2, target.acquirerType());
        assertEquals("card-authorization-service-2", target.serviceName());
    }

    @Test
    void shouldUseDefaultAcquirerForUnknownBrand() {
        BinBasedRoutingPolicy policy = new BinBasedRoutingPolicy(binResolver, createRoutingProperties());

        RoutingTarget target = policy.route(createRequest("9111111111111111"));

        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE, target.acquirerType());
        assertEquals("card-authorization-service", target.serviceName());
    }

    private RoutingProperties createRoutingProperties() {
        RoutingProperties properties = new RoutingProperties();
        properties.setDefaultAcquirer(AcquirerType.CARD_AUTHORIZATION_SERVICE);

        EnumMap<CardBrand, AcquirerType> brandMap = new EnumMap<>(CardBrand.class);
        brandMap.put(CardBrand.VISA, AcquirerType.CARD_AUTHORIZATION_SERVICE);
        brandMap.put(CardBrand.MASTERCARD, AcquirerType.CARD_AUTHORIZATION_SERVICE_2);
        brandMap.put(CardBrand.AMEX, AcquirerType.CARD_AUTHORIZATION_SERVICE_2);
        brandMap.put(CardBrand.JCB, AcquirerType.CARD_AUTHORIZATION_SERVICE_2);
        brandMap.put(CardBrand.DISCOVER, AcquirerType.CARD_AUTHORIZATION_SERVICE_2);
        properties.setBrandAcquirerMap(brandMap);
        return properties;
    }

    private MerchantApprovalRequest createRequest(String cardNumber) {
        return MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603210001")
                .merchantId("MERCHANT-001")
                .cardNumber(cardNumber)
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();
    }
}
