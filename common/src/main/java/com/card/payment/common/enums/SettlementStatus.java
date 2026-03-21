package com.card.payment.common.enums;

/**
 * 정산 상태
 */
public enum SettlementStatus {
    PENDING("대기"),
    PROCESSING("처리중"),
    COMPLETED("완료"),
    FAILED("실패");
    
    private final String description;
    
    SettlementStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
