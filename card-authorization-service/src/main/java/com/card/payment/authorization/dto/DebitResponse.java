package com.card.payment.authorization.dto;

import java.math.BigDecimal;

/**
 * 은행 출금 응답 DTO
 */
public class DebitResponse {
    private boolean success;
    private String transactionId;
    private BigDecimal balanceAfter;
    private String message;

    public DebitResponse() {
    }

    public DebitResponse(boolean success, String transactionId, BigDecimal balanceAfter, String message) {
        this.success = success;
        this.transactionId = transactionId;
        this.balanceAfter = balanceAfter;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
