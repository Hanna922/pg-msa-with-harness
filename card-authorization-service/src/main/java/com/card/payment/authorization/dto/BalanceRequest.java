package com.card.payment.authorization.dto;

import java.math.BigDecimal;

/**
 * 은행 잔액 조회 요청 DTO
 */
public class BalanceRequest {
    private String cardNumber;
    private BigDecimal amount;

    public BalanceRequest() {
    }

    public BalanceRequest(String cardNumber, BigDecimal amount) {
        this.cardNumber = cardNumber;
        this.amount = amount;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
