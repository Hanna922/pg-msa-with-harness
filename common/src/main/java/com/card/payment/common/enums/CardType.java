package com.card.payment.common.enums;

/**
 * 카드 종류
 */
public enum CardType {
    CREDIT("신용카드"),
    DEBIT("체크카드");
    
    private final String description;
    
    CardType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
