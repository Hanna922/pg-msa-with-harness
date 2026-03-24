package dev.pg.routing.model;

public enum CardBrand {
    VISA(AcquirerType.CARD_AUTHORIZATION_SERVICE),
    MASTERCARD(AcquirerType.CARD_AUTHORIZATION_SERVICE_2),
    AMEX(AcquirerType.CARD_AUTHORIZATION_SERVICE_2),
    JCB(AcquirerType.CARD_AUTHORIZATION_SERVICE_2),
    DISCOVER(AcquirerType.CARD_AUTHORIZATION_SERVICE_2),
    UNKNOWN(AcquirerType.CARD_AUTHORIZATION_SERVICE);

    private final AcquirerType defaultAcquirerType;

    CardBrand(AcquirerType defaultAcquirerType) {
        this.defaultAcquirerType = defaultAcquirerType;
    }

    public AcquirerType getDefaultAcquirerType() {
        return defaultAcquirerType;
    }
}
