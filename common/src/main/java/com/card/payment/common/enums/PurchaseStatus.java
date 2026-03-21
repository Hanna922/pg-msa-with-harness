package com.card.payment.common.enums;

/**
 * 매입 상태
 */
public enum PurchaseStatus {
    PENDING("대기"),
    CONFIRMED("확정"),
    REJECTED("거절");
    
    private final String description;
    
    PurchaseStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
