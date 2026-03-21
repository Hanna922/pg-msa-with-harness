package com.card.payment.common.code;

/**
 * 승인/거절 응답 코드
 */
public enum ResponseCode {
    // 승인
    APPROVED("00", "승인"),
    
    // 거절 코드
    INVALID_CARD("14", "유효하지 않은 카드"),
    INSUFFICIENT_BALANCE("51", "잔액 부족"),
    EXPIRED_CARD("54", "유효기간 만료"),
    INVALID_PIN("55", "PIN 불일치"),
    CREDIT_LIMIT_EXCEEDED("61", "신용 한도 초과"),
    DUPLICATE_TRANSACTION("94", "중복 거래"),
    
    // 시스템 오류
    SYSTEM_ERROR("96", "시스템 오류"),
    TIMEOUT("68", "타임아웃"),
    
    // 기타
    INVALID_MERCHANT("03", "유효하지 않은 가맹점"),
    INVALID_AMOUNT("13", "유효하지 않은 금액");
    
    private final String code;
    private final String message;
    
    ResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public static ResponseCode fromCode(String code) {
        for (ResponseCode responseCode : values()) {
            if (responseCode.code.equals(code)) {
                return responseCode;
            }
        }
        return SYSTEM_ERROR;
    }
}
