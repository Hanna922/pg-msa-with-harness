package com.card.payment.bank.repository;

import com.card.payment.bank.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 계좌 리포지토리
 * 요구사항: 4.2, 4.3, 12.1
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    
    /**
     * 계좌 번호로 계좌 조회
     */
    Optional<Account> findByAccountNumber(String accountNumber);
    
    /**
     * 계좌 번호로 계좌 조회 (비관적 락 적용)
     * 동시성 제어를 위해 비관적 락 사용
     * 요구사항: 12.1
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberWithLock(@Param("accountNumber") String accountNumber);
}
