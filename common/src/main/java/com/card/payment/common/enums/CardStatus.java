package com.card.payment.common.enums;

/**
 * 카드 상태
 */
public enum CardStatus {
    ACTIVE("정상"),
    SUSPENDED("정지"),
    LOST("분실"),
    TERMINATED("해지");
    
    private final String description;
    
    CardStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
