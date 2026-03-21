package com.card.payment.bank.service;

import com.card.payment.bank.entity.Account;
import com.card.payment.bank.entity.Transaction;
import com.card.payment.bank.entity.Transfer;
import com.card.payment.bank.repository.AccountRepository;
import com.card.payment.bank.repository.TransactionRepository;
import com.card.payment.bank.repository.TransferRepository;
import com.card.payment.common.enums.AccountStatus;
import com.card.payment.common.enums.TransactionType;
import com.card.payment.common.enums.TransferStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 이체 서비스
 * 계좌 간 이체 처리 기능 제공
 * 요구사항: 11.4, 11.5, 11.6, 11.7, 11.8, 11.9, 11.10, 13.1, 13.2, 13.3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {
    
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final TransactionRepository transactionRepository;
    
    /**
     * 출금 계좌 검증
     * 계좌의 존재 여부, 상태, 잔액을 확인
     * 요구사항: 11.4, 11.5
     * 
     * @param accountNumber 출금 계좌 번호
     * @param amount 이체 금액
     * @return 검증 결과
     */
    public AccountValidationResult validateFromAccount(String accountNumber, BigDecimal amount) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return AccountValidationResult.failure("출금 계좌 번호는 필수입니다");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return AccountValidationResult.failure("이체 금액은 0보다 커야 합니다");
        }
        
        log.debug("출금 계좌 검증 시작: accountNumber={}, amount={}", accountNumber, amount);
        
        // 계좌 존재 여부 확인
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElse(null);
        
        if (account == null) {
            log.warn("출금 계좌를 찾을 수 없음: accountNumber={}", accountNumber);
            return AccountValidationResult.failure("출금 계좌를 찾을 수 없습니다");
        }
        
        // 계좌 상태 확인
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            log.warn("출금 계좌 상태가 정상이 아님: accountNumber={}, status={}", 
                    accountNumber, account.getAccountStatus());
            return AccountValidationResult.failure("출금 계좌 상태가 정상이 아닙니다: " + account.getAccountStatus().getDescription());
        }
        
        // 잔액 확인
        BigDecimal availableBalance = account.getAvailableBalance();
        if (amount.compareTo(availableBalance) > 0) {
            log.warn("출금 계좌 잔액 부족: accountNumber={}, requestAmount={}, availableBalance={}", 
                    accountNumber, amount, availableBalance);
            return AccountValidationResult.failure("출금 계좌의 잔액이 부족합니다");
        }
        
        log.debug("출금 계좌 검증 완료: accountNumber={}", accountNumber);
        return AccountValidationResult.success();
    }
    
    /**
     * 입금 계좌 검증
     * 계좌의 존재 여부와 상태를 확인
     * 요구사항: 11.6
     * 
     * @param accountNumber 입금 계좌 번호
     * @return 검증 결과
     */
    public AccountValidationResult validateToAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return AccountValidationResult.failure("입금 계좌 번호는 필수입니다");
        }
        
        log.debug("입금 계좌 검증 시작: accountNumber={}", accountNumber);
        
        // 계좌 존재 여부 확인
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElse(null);
        
        if (account == null) {
            log.warn("입금 계좌를 찾을 수 없음: accountNumber={}", accountNumber);
            return AccountValidationResult.failure("입금 계좌를 찾을 수 없습니다");
        }
        
        // 계좌 상태 확인
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            log.warn("입금 계좌 상태가 정상이 아님: accountNumber={}, status={}", 
                    accountNumber, account.getAccountStatus());
            return AccountValidationResult.failure("입금 계좌 상태가 정상이 아닙니다: " + account.getAccountStatus().getDescription());
        }
        
        log.debug("입금 계좌 검증 완료: accountNumber={}", accountNumber);
        return AccountValidationResult.success();
    }
    
    /**
     * 이체 처리
     * 트랜잭션 내에서 출금과 입금을 처리하고 거래 내역을 저장
     * 요구사항: 11.7, 11.8, 11.9, 11.10, 13.1, 13.2, 13.3
     * 
     * @param fromAccountNumber 출금 계좌 번호
     * @param toAccountNumber 입금 계좌 번호
     * @param amount 이체 금액
     * @param settlementId 정산 ID (정산 이체인 경우)
     * @return 이체 처리 결과
     */
    @Transactional
    public TransferResult processTransfer(String fromAccountNumber, String toAccountNumber, 
                                         BigDecimal amount, String settlementId) {
        log.info("이체 처리 시작: fromAccount={}, toAccount={}, amount={}, settlementId={}", 
                fromAccountNumber, toAccountNumber, amount, settlementId);
        
        // 이체 고유 번호 생성
        String transferId = generateTransferId();
        LocalDateTime transferDate = LocalDateTime.now();
        
        try {
            // 출금 계좌 검증
            AccountValidationResult fromValidation = validateFromAccount(fromAccountNumber, amount);
            if (!fromValidation.isValid()) {
                log.error("출금 계좌 검증 실패: {}", fromValidation.getErrorMessage());
                saveFailedTransfer(transferId, fromAccountNumber, toAccountNumber, amount, 
                        settlementId, transferDate, fromValidation.getErrorMessage());
                return TransferResult.failure(transferId, fromValidation.getErrorMessage());
            }
            
            // 입금 계좌 검증
            AccountValidationResult toValidation = validateToAccount(toAccountNumber);
            if (!toValidation.isValid()) {
                log.error("입금 계좌 검증 실패: {}", toValidation.getErrorMessage());
                saveFailedTransfer(transferId, fromAccountNumber, toAccountNumber, amount, 
                        settlementId, transferDate, toValidation.getErrorMessage());
                return TransferResult.failure(transferId, toValidation.getErrorMessage());
            }
            
            // 비관적 락을 사용하여 출금 계좌 조회 (동시성 제어)
            Account fromAccount = accountRepository.findByAccountNumberWithLock(fromAccountNumber)
                    .orElseThrow(() -> new IllegalStateException("출금 계좌를 찾을 수 없습니다"));
            
            // 비관적 락을 사용하여 입금 계좌 조회 (동시성 제어)
            Account toAccount = accountRepository.findByAccountNumberWithLock(toAccountNumber)
                    .orElseThrow(() -> new IllegalStateException("입금 계좌를 찾을 수 없습니다"));
            
            // 출금 처리
            fromAccount.debit(amount);
            accountRepository.save(fromAccount);
            
            log.debug("출금 처리 완료: accountNumber={}, amount={}, balanceAfter={}", 
                    fromAccountNumber, amount, fromAccount.getBalance());
            
            // 입금 처리
            toAccount.credit(amount);
            accountRepository.save(toAccount);
            
            log.debug("입금 처리 완료: accountNumber={}, amount={}, balanceAfter={}", 
                    toAccountNumber, amount, toAccount.getBalance());
            
            // 출금 거래 내역 저장
            Transaction debitTransaction = Transaction.builder()
                    .transactionId(generateTransactionId())
                    .accountNumber(fromAccountNumber)
                    .transactionType(TransactionType.TRANSFER)
                    .amount(amount)
                    .balanceAfter(fromAccount.getBalance())
                    .referenceId(transferId)
                    .description("이체 출금 (수취인: " + toAccountNumber + ")")
                    .transactionDate(transferDate)
                    .build();
            transactionRepository.save(debitTransaction);
            
            // 입금 거래 내역 저장
            Transaction creditTransaction = Transaction.builder()
                    .transactionId(generateTransactionId())
                    .accountNumber(toAccountNumber)
                    .transactionType(TransactionType.TRANSFER)
                    .amount(amount)
                    .balanceAfter(toAccount.getBalance())
                    .referenceId(transferId)
                    .description("이체 입금 (송금인: " + fromAccountNumber + ")")
                    .transactionDate(transferDate)
                    .build();
            transactionRepository.save(creditTransaction);
            
            // 이체 내역 저장 (성공)
            Transfer transfer = Transfer.builder()
                    .transferId(transferId)
                    .settlementId(settlementId)
                    .fromAccount(fromAccountNumber)
                    .toAccount(toAccountNumber)
                    .amount(amount)
                    .status(TransferStatus.SUCCESS)
                    .failureReason(null)
                    .transferDate(transferDate)
                    .build();
            transferRepository.save(transfer);
            
            log.info("이체 처리 완료: transferId={}, fromAccount={}, toAccount={}, amount={}", 
                    transferId, fromAccountNumber, toAccountNumber, amount);
            
            return TransferResult.success(transferId, fromAccountNumber, toAccountNumber, 
                    amount, transferDate);
            
        } catch (Exception e) {
            log.error("이체 처리 중 오류 발생: transferId={}, error={}", transferId, e.getMessage(), e);
            
            // 이체 내역 저장 (실패)
            saveFailedTransfer(transferId, fromAccountNumber, toAccountNumber, amount, 
                    settlementId, transferDate, e.getMessage());
            
            return TransferResult.failure(transferId, e.getMessage());
        }
    }
    
    /**
     * 실패한 이체 내역 저장
     * 
     * @param transferId 이체 고유 번호
     * @param fromAccount 출금 계좌 번호
     * @param toAccount 입금 계좌 번호
     * @param amount 이체 금액
     * @param settlementId 정산 ID
     * @param transferDate 이체 일시
     * @param failureReason 실패 사유
     */
    private void saveFailedTransfer(String transferId, String fromAccount, String toAccount, 
                                   BigDecimal amount, String settlementId, 
                                   LocalDateTime transferDate, String failureReason) {
        try {
            Transfer transfer = Transfer.builder()
                    .transferId(transferId)
                    .settlementId(settlementId)
                    .fromAccount(fromAccount)
                    .toAccount(toAccount)
                    .amount(amount)
                    .status(TransferStatus.FAILED)
                    .failureReason(failureReason)
                    .transferDate(transferDate)
                    .build();
            transferRepository.save(transfer);
        } catch (Exception e) {
            log.error("실패한 이체 내역 저장 중 오류 발생: transferId={}, error={}", transferId, e.getMessage());
        }
    }
    
    /**
     * 이체 고유 번호 생성
     * UUID 기반으로 고유한 이체 번호를 생성
     * 요구사항: 11.10
     * 
     * @return 이체 고유 번호
     */
    public String generateTransferId() {
        return "TRF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
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
     * 계좌 검증 결과 DTO
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class AccountValidationResult {
        private boolean valid;
        private String errorMessage;
        
        public static AccountValidationResult success() {
            return AccountValidationResult.builder()
                    .valid(true)
                    .errorMessage(null)
                    .build();
        }
        
        public static AccountValidationResult failure(String errorMessage) {
            return AccountValidationResult.builder()
                    .valid(false)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
    
    /**
     * 이체 처리 결과 DTO
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class TransferResult {
        private boolean success;
        private String transferId;
        private String fromAccount;
        private String toAccount;
        private BigDecimal amount;
        private LocalDateTime transferDate;
        private String failureReason;
        
        public static TransferResult success(String transferId, String fromAccount, 
                                            String toAccount, BigDecimal amount, 
                                            LocalDateTime transferDate) {
            return TransferResult.builder()
                    .success(true)
                    .transferId(transferId)
                    .fromAccount(fromAccount)
                    .toAccount(toAccount)
                    .amount(amount)
                    .transferDate(transferDate)
                    .failureReason(null)
                    .build();
        }
        
        public static TransferResult failure(String transferId, String failureReason) {
            return TransferResult.builder()
                    .success(false)
                    .transferId(transferId)
                    .fromAccount(null)
                    .toAccount(null)
                    .amount(null)
                    .transferDate(null)
                    .failureReason(failureReason)
                    .build();
        }
    }
}
