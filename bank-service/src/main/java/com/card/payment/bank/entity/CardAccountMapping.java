package com.card.payment.bank.entity;

import com.card.payment.common.enums.CardType;
import com.card.payment.common.enums.MappingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 카드-계좌 매핑 엔티티
 * 체크카드와 은행 계좌를 연결하는 매핑 정보
 * 요구사항: 4.2
 */
@Entity
@Table(name = "card_account_mappings")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CardAccountMapping {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 카드 번호 (암호화 저장)
     */
    @Column(nullable = false, length = 255)
    private String cardNumber;
    
    /**
     * 계좌 번호
     */
    @Column(nullable = false, length = 20)
    private String accountNumber;
    
    /**
     * 카드 종류 (주로 DEBIT)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardType cardType;
    
    /**
     * 매핑 상태 (활성, 비활성)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MappingStatus status;
    
    /**
     * 생성 일시
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
