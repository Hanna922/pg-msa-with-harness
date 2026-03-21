package com.card.payment.bank.entity;

import com.card.payment.common.enums.AccountStatus;
import com.card.payment.common.enums.AccountType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 계좌 엔티티
 * 요구사항: 4.2, 4.3
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Account {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 계좌 번호 (고유)
     */
    @Column(unique = true, nullable = false, length = 20)
    private String accountNumber;
    
    /**
     * 은행 코드
     */
    @Column(nullable = false, length = 10)
    private String bankCode;
    
    /**
     * 계좌 종류 (입출금, 저축)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType accountType;
    
    /**
     * 계좌 상태 (정상, 정지, 해지)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus accountStatus;
    
    /**
     * 현재 잔액
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;
    
    /**
     * 최소 잔액 (출금 가능 금액 계산 시 사용)
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal minimumBalance;
    
    /**
     * 고객 ID
     */
    @Column(nullable = false, length = 50)
    private String customerId;
    
    /**
     * 생성 일시
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * 낙관적 락을 위한 버전 필드
     */
    @Version
    private Long version;
    
    /**
     * 잔액 차감 (출금)
     */
    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("출금 금액은 0보다 커야 합니다");
        }
        
        BigDecimal availableBalance = this.balance.subtract(this.minimumBalance);
        if (amount.compareTo(availableBalance) > 0) {
            throw new IllegalStateException("출금 가능 금액이 부족합니다");
        }
        
        this.balance = this.balance.subtract(amount);
    }
    
    /**
     * 잔액 증가 (입금)
     */
    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("입금 금액은 0보다 커야 합니다");
        }
        
        this.balance = this.balance.add(amount);
    }
    
    /**
     * 출금 가능 금액 계산
     */
    public BigDecimal getAvailableBalance() {
        return this.balance.subtract(this.minimumBalance);
    }
}
