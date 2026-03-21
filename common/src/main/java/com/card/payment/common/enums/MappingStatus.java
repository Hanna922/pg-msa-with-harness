package com.card.payment.common.enums;

/**
 * 매핑 상태
 */
public enum MappingStatus {
    ACTIVE("활성"),
    INACTIVE("비활성");
    
    private final String description;
    
    MappingStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
