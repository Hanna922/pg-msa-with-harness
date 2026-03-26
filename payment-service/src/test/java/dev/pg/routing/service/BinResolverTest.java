package dev.pg.routing.service;

import dev.pg.routing.model.CardBrand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BinResolverTest {

    private final BinResolver binResolver = new BinResolver();

    @Test
    void shouldResolveVisaCard() {
        assertEquals(CardBrand.VISA, binResolver.resolve("4111111111111111"));
    }

    @Test
    void shouldResolveMastercardCard() {
        assertEquals(CardBrand.MASTERCARD, binResolver.resolve("5555555555554444"));
    }

    @Test
    void shouldResolveAmexCard() {
        assertEquals(CardBrand.AMEX, binResolver.resolve("378282246310005"));
    }

    @Test
    void shouldResolveDiscoverCard() {
        assertEquals(CardBrand.DISCOVER, binResolver.resolve("6011111111111117"));
    }

    @Test
    void shouldResolveJcbCardBeforeGenericThreePrefix() {
        assertEquals(CardBrand.JCB, binResolver.resolve("3530111333300000"));
    }

    @Test
    void shouldReturnUnknownForUnmatchedPrefix() {
        assertEquals(CardBrand.UNKNOWN, binResolver.resolve("9111111111111111"));
    }

    @Test
    void shouldReturnUnknownForBlankCardNumber() {
        assertEquals(CardBrand.UNKNOWN, binResolver.resolve("  "));
    }
}
