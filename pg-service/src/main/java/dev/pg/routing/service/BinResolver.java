package dev.pg.routing.service;

import dev.pg.routing.model.CardBrand;
import org.springframework.stereotype.Component;

@Component
public class BinResolver {

    public CardBrand resolve(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return CardBrand.UNKNOWN;
        }

        String normalizedCardNumber = cardNumber.trim();

        if (normalizedCardNumber.startsWith("35")) {
            return CardBrand.JCB;
        }
        if (normalizedCardNumber.startsWith("3")) {
            return CardBrand.AMEX;
        }
        if (normalizedCardNumber.startsWith("4")) {
            return CardBrand.VISA;
        }
        if (normalizedCardNumber.startsWith("5")) {
            return CardBrand.MASTERCARD;
        }
        if (normalizedCardNumber.startsWith("6")) {
            return CardBrand.DISCOVER;
        }

        return CardBrand.UNKNOWN;
    }
}
