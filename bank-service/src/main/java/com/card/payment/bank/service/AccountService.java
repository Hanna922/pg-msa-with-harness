package com.card.payment.bank.service;

import com.card.payment.bank.entity.Account;
import com.card.payment.bank.entity.CardAccountMapping;
import com.card.payment.bank.entity.Transaction;
import com.card.payment.bank.repository.AccountRepository;
import com.card.payment.bank.repository.CardAccountMappingRepository;
import com.card.payment.bank.repository.TransactionRepository;
import com.card.payment.common.enums.AccountStatus;
import com.card.payment.common.enums.MappingStatus;
import com.card.payment.common.enums.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 계좌 서비스
 * 계좌 조회, 잔액 조회, 출금 처리 등의 기능 제공
 * 요구사항: 4.2, 4.3, 4.7, 4.8, 12.1, 12.3, 13.1, 13.2, 13.3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    
    private final AccountRepository accountRepository;
    private final CardAccountMappingRepository cardAccountMappingRepository;
    private final TransactionRepository transactionRepository;
    
    /**
     * 카드 번호로 계좌 조회
     * 체크카드의 경우 카드-계좌 매핑 테이블을 통해 연결된 계좌를 조회
     * 요구사항: 4.2
     * 
     * @param cardNumber 카드 번호 (암호화된 상태)
     * @return 계좌 정보
     * @throws IllegalArgumentException 카드 번호가 null이거나 빈 문자열인 경우
     * @throws IllegalStateException 매핑 정보가 없거나 계좌를 찾을 수 없는 경우
     */
    public Account findAccountByCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("카드 번호는 필수입니다");
        }
        
        log.debug("카드 번호로 계좌 조회 시작: cardNumber={}", cardNumber);
        
        // 카드-계좌 매핑 조회
        CardAccountMapping mapping = cardAccountMappingRepository
                .findByCardNumberAndStatus(cardNumber, MappingStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("활성 상태의 카드-계좌 매핑을 찾을 수 없습니다"));
        
        // 계좌 조회
        Account account = accountRepository
                .findByAccountNumber(mapping.getAccountNumber())
                .orElseThrow(() -> new IllegalStateException("계좌를 찾을 수 없습니다: " + mapping.getAccountNumber()));
        
        log.debug("계좌 조회 완료: accountNumber={}", account.getAccountNumber());
        return account;
    }
    
    /**
     * 잔액 조회 (비관적 락 적용)
     * 동시성 제어를 위해 비관적 락을 사용하여 계좌 정보를 조회
     * 요구사항: 4.3, 12.1
     * 
     * @param accountNumber 계좌 번호
     * @param requestAmount 요청 금액 (출금 가능 여부 확인용)
     * @return 잔액 조회 결과 (계좌 정보, 출금 가능 여부)
     * @throws IllegalArgumentException 계좌 번호가 null이거나 요청 금액이 0 이하인 경우
     * @throws IllegalStateException 계좌를 찾을 수 없거나 계좌 상태가 정상이 아닌 경우
     */
    @Transactional
    public BalanceCheckResult checkBalance(String accountNumber, BigDecimal requestAmount) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("계좌 번호는 필수입니다");
        }
        
        if (requestAmount == null || requestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("요청 금액은 0보다 커야 합니다");
        }
        
        log.debug("잔액 조회 시작: accountNumber={}, requestAmount={}", accountNumber, requestAmount);
        
        // 비관적 락을 사용하여 계좌 조회
        Account account = accountRepository
                .findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new IllegalStateException("계좌를 찾을 수 없습니다: " + accountNumber));
        
        // 계좌 상태 확인
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("계좌 상태가 정상이 아닙니다: " + account.getAccountStatus());
        }
        
        // 출금 가능 금액 계산
        BigDecimal availableBalance = calculateAvailableBalance(account);
        boolean canWithdraw = requestAmount.compareTo(availableBalance) <= 0;
        
        log.debug("잔액 조회 완료: balance={}, availableBalance={}, canWithdraw={}", 
                account.getBalance(), availableBalance, canWithdraw);
        
        return BalanceCheckResult.builder()
                .accountNumber(accountNumber)
                .balance(account.getBalance())
                .minimumBalance(account.getMinimumBalance())
                .availableBalance(availableBalance)
                .requestAmount(requestAmount)
                .canWithdraw(canWithdraw)
                .build();
    }
    
    /**
     * 출금 가능 금액 계산
     * 현재 잔액에서 최소 잔액을 차감한 금액을 반환
     * 요구사항: 4.3
     * 
     * @param account 계좌 정보
     * @return 출금 가능 금액
     */
    public BigDecimal calculateAvailableBalance(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("계좌 정보는 필수입니다");
        }
        
        return account.getAvailableBalance();
    }
    
    /**
     * 출금 처리
     * 트랜잭션 내에서 계좌 잔액을 차감하고 거래 내역을 저장
     * 요구사항: 4.7, 4.8, 12.3, 13.1, 13.2, 13.3
     * 
     * @param cardNumber 카드 번호 (암호화된 상태)
     * @param amount 출금 금액
     * @param referenceId 참조 ID (승인 번호 등)
     * @return 출금 처리 결과
     * @throws IllegalArgumentException 입력 파라미터가 유효하지 않은 경우
     * @throws IllegalStateException 계좌를 찾을 수 없거나 출금이 불가능한 경우
     */
    @Transactional
    public DebitResult processDebit(String cardNumber, BigDecimal amount, String referenceId) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("카드 번호는 필수입니다");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("출금 금액은 0보다 커야 합니다");
        }
        
        log.info("출금 처리 시작: cardNumber={}, amount={}, referenceId={}", cardNumber, amount, referenceId);
        
        // 카드 번호로 계좌 조회
        CardAccountMapping mapping = cardAccountMappingRepository
                .findByCardNumberAndStatus(cardNumber, MappingStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("활성 상태의 카드-계좌 매핑을 찾을 수 없습니다"));
        
        // 비관적 락을 사용하여 계좌 조회 (동시성 제어)
        Account account = accountRepository
                .findByAccountNumberWithLock(mapping.getAccountNumber())
                .orElseThrow(() -> new IllegalStateException("계좌를 찾을 수 없습니다: " + mapping.getAccountNumber()));
        
        // 계좌 상태 확인
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("계좌 상태가 정상이 아닙니다: " + account.getAccountStatus());
        }
        
        // 출금 가능 금액 확인
        BigDecimal availableBalance = calculateAvailableBalance(account);
        if (amount.compareTo(availableBalance) > 0) {
            log.warn("출금 가능 금액 부족: requestAmount={}, availableBalance={}", amount, availableBalance);
            throw new IllegalStateException("출금 가능 금액이 부족합니다");
        }
        
        // 잔액 차감
        account.debit(amount);
        accountRepository.save(account);
        
        // 거래 내역 저장
        String transactionId = generateTransactionId();
        Transaction transaction = Transaction.builder()
                .transactionId(transactionId)
                .accountNumber(account.getAccountNumber())
                .transactionType(TransactionType.DEBIT)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .referenceId(referenceId)
                .description("카드 결제 출금")
                .transactionDate(LocalDateTime.now())
                .build();
        
        transactionRepository.save(transaction);
        
        log.info("출금 처리 완료: transactionId={}, accountNumber={}, amount={}, balanceAfter={}", 
                transactionId, account.getAccountNumber(), amount, account.getBalance());
        
        return DebitResult.builder()
                .success(true)
                .transactionId(transactionId)
                .accountNumber(account.getAccountNumber())
                .amount(amount)
                .balanceAfter(account.getBalance())
                .transactionDate(transaction.getTransactionDate())
                .build();
    }
    
    /**
     * 거래 고유 번호 생성
     * UUID 기반으로 고유한 거래 번호를 생성
     * 
     * @return 거래 고유 번호
     */
    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }
    
    /**
     * 잔액 조회 결과 DTO
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class BalanceCheckResult {
        private String accountNumber;
        private BigDecimal balance;
        private BigDecimal minimumBalance;
        private BigDecimal availableBalance;
        private BigDecimal requestAmount;
        private boolean canWithdraw;
    }
    
    /**
     * 출금 처리 결과 DTO
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class DebitResult {
        private boolean success;
        private String transactionId;
        private String accountNumber;
        private BigDecimal amount;
        private BigDecimal balanceAfter;
        private LocalDateTime transactionDate;
    }
}
