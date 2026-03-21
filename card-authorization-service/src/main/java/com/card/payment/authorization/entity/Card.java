package com.card.payment.authorization.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 카드 엔티티
 * 카드 정보를 저장하며, 신용카드와 체크카드를 모두 지원합니다.
 */
@Entity
@Table(name = "cards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Card {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 카드 번호 (암호화 저장)
     */
    @Column(unique = true, nullable = false, length = 500)
    private String cardNumber;
    
    /**
     * 카드 타입 (CREDIT: 신용카드, DEBIT: 체크카드)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardType cardType;
    
    /**
     * 카드 상태 (ACTIVE: 정상, SUSPENDED: 정지, LOST: 분실, TERMINATED: 해지)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStatus cardStatus;
    
    /**
     * 유효기간 (YYMM 형식)
     */
    @Column(nullable = false)
    private LocalDate expiryDate;
    
    /**
     * 신용 한도 (신용카드만 해당)
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal creditLimit;
    
    /**
     * 사용 금액 (신용카드만 해당)
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal usedAmount;
    
    /**
     * PIN (암호화 저장)
     */
    @Column(length = 500)
    private String pin;
    
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
}
