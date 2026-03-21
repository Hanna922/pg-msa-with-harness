package com.card.payment.authorization.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 승인 엔티티
 * 카드 승인 요청 및 응답 정보를 저장합니다.
 */
@Entity
@Table(name = "authorizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Authorization {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 거래 고유 번호
     */
    @Column(unique = true, nullable = false, length = 100)
    private String transactionId;
    
    /**
     * 카드 번호 (마스킹 처리, 예: 1234-****-****-5678)
     */
    @Column(nullable = false, length = 19)
    private String cardNumberMasked;
    
    /**
     * 거래 금액
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    
    /**
     * 가맹점 ID
     */
    @Column(nullable = false, length = 50)
    private String merchantId;
    
    /**
     * 승인 번호 (8자리)
     */
    @Column(length = 8)
    private String approvalNumber;
    
    /**
     * 응답 코드 (00: 승인, 14: 카드정지, 51: 잔액부족, 54: 유효기간만료, 55: PIN오류, 61: 한도초과, 94: 중복거래)
     */
    @Column(nullable = false, length = 2)
    private String responseCode;
    
    /**
     * 승인 상태 (APPROVED: 승인, REJECTED: 거절, CONFIRMED: 매출확정)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthorizationStatus status;
    
    /**
     * 승인 일시
     */
    @Column(nullable = false)
    private LocalDateTime authorizationDate;
    
    /**
     * 생성 일시
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
