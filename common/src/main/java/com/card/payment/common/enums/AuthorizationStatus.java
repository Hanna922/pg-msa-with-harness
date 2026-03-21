package com.card.payment.common.enums;

/**
 * 승인 상태
 */
public enum AuthorizationStatus {
    APPROVED("승인"),
    REJECTED("거절"),
    CONFIRMED("확정");
    
    private final String description;
    
    AuthorizationStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
