package com.card.payment.common.enums;

/**
 * 계좌 종류
 */
public enum AccountType {
    CHECKING("입출금"),
    SAVINGS("저축");
    
    private final String description;
    
    AccountType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
