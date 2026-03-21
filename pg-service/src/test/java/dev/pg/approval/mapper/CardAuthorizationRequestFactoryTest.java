package dev.pg.approval.mapper;

import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.MerchantApprovalRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CardAuthorizationRequestFactoryTest {

    private final CardAuthorizationRequestFactory factory = new CardAuthorizationRequestFactory();

    @Test
    void shouldCreateCardAuthorizationRequestFromMerchantApprovalRequest() {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603210003")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();

        CardAuthorizationRequest cardAuthorizationRequest =
                factory.create(request, "PG20260321194000ABCDEF");

        assertEquals("PG20260321194000ABCDEF", cardAuthorizationRequest.getTransactionId());
        assertEquals("4111111111111111", cardAuthorizationRequest.getCardNumber());
        assertEquals(new BigDecimal("10000"), cardAuthorizationRequest.getAmount());
        assertEquals("MERCHANT-001", cardAuthorizationRequest.getMerchantId());
        assertNull(cardAuthorizationRequest.getTerminalId());
        assertNull(cardAuthorizationRequest.getPin());
    }
}
