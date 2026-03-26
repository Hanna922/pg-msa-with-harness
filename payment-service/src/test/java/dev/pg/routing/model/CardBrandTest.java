package dev.pg.routing.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardBrandTest {

    @Test
    void shouldExposeDefaultAcquirerForEachBrand() {
        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE, CardBrand.VISA.getDefaultAcquirerType());
        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE_2, CardBrand.MASTERCARD.getDefaultAcquirerType());
        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE_2, CardBrand.AMEX.getDefaultAcquirerType());
        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE_2, CardBrand.JCB.getDefaultAcquirerType());
        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE_2, CardBrand.DISCOVER.getDefaultAcquirerType());
        assertEquals(AcquirerType.CARD_AUTHORIZATION_SERVICE, CardBrand.UNKNOWN.getDefaultAcquirerType());
    }
}
