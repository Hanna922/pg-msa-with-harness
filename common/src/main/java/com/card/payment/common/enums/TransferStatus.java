package com.card.payment.common.enums;

/**
 * 이체 상태
 */
public enum TransferStatus {
    PENDING("대기"),
    SUCCESS("성공"),
    FAILED("실패");
    
    private final String description;
    
    TransferStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
