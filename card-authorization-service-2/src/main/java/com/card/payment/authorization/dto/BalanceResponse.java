package com.card.payment.authorization.dto;

import java.math.BigDecimal;

/**
 * 은행 잔액 조회 응답 DTO
 * Bank Service의 응답 형식과 일치
 */
public class BalanceResponse {
    private String accountNumber;
    private BigDecimal balance;
    private BigDecimal minimumBalance;
    private BigDecimal availableBalance;
    private BigDecimal requestAmount;
    private boolean canWithdraw;
    private String responseCode;
    private String responseMessage;

    public BalanceResponse() {
    }

    public BalanceResponse(String accountNumber, BigDecimal balance, BigDecimal minimumBalance, 
                          BigDecimal availableBalance, BigDecimal requestAmount, boolean canWithdraw,
                          String responseCode, String responseMessage) {
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.minimumBalance = minimumBalance;
        this.availableBalance = availableBalance;
        this.requestAmount = requestAmount;
        this.canWithdraw = canWithdraw;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
    }

    // 하위 호환성을 위한 메서드
    public boolean isSufficient() {
        return canWithdraw;
    }

    public BigDecimal getAvailableAmount() {
        return availableBalance;
    }

    public String getMessage() {
        return responseMessage;
    }

    // Getters and Setters
    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getMinimumBalance() {
        return minimumBalance;
    }

    public void setMinimumBalance(BigDecimal minimumBalance) {
        this.minimumBalance = minimumBalance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getRequestAmount() {
        return requestAmount;
    }

    public void setRequestAmount(BigDecimal requestAmount) {
        this.requestAmount = requestAmount;
    }

    public boolean isCanWithdraw() {
        return canWithdraw;
    }

    public void setCanWithdraw(boolean canWithdraw) {
        this.canWithdraw = canWithdraw;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }
}
