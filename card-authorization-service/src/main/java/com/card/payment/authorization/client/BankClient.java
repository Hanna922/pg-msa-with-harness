package com.card.payment.authorization.client;

import com.card.payment.authorization.dto.BalanceResponse;
import com.card.payment.authorization.dto.DebitResponse;

import java.math.BigDecimal;

/**
 * 은행 서비스 클라이언트 인터페이스
 */
public interface BankClient {
    
    /**
     * 은행 잔액 조회
     * @param cardNumber 카드 번호
     * @param amount 요청 금액
     * @return 잔액 조회 응답
     */
    BalanceResponse checkBalance(String cardNumber, BigDecimal amount);
    
    /**
     * 은행 출금 요청
     * @param cardNumber 카드 번호
     * @param amount 출금 금액
     * @param transactionId 거래 ID
     * @return 출금 응답
     */
    DebitResponse requestDebit(String cardNumber, BigDecimal amount, String transactionId);
}
