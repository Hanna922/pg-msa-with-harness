package com.card.payment.common.enums;

/**
 * 거래 상태
 */
public enum TransactionStatus {
    PENDING("대기"),
    APPROVED("승인"),
    REJECTED("거절"),
    CONFIRMED("확정"),
    CANCELLED("취소");
    
    private final String description;
    
    TransactionStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
