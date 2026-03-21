package com.card.payment.common.enums;

/**
 * 거래 유형
 */
public enum TransactionType {
    DEBIT("출금"),
    CREDIT("입금"),
    TRANSFER("이체");
    
    private final String description;
    
    TransactionType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
