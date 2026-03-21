package com.card.payment.bank.entity;

import com.card.payment.common.enums.TransferStatus;
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
 * 이체 내역 엔티티
 * 계좌 간 이체 거래를 기록
 * 요구사항: 11.4, 11.5, 11.6, 11.10
 */
@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Transfer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 이체 고유 번호
     */
    @Column(unique = true, nullable = false, length = 50)
    private String transferId;
    
    /**
     * 정산 ID (정산 이체인 경우)
     */
    @Column(length = 50)
    private String settlementId;
    
    /**
     * 출금 계좌 번호
     */
    @Column(nullable = false, length = 20)
    private String fromAccount;
    
    /**
     * 입금 계좌 번호
     */
    @Column(nullable = false, length = 20)
    private String toAccount;
    
    /**
     * 이체 금액
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    
    /**
     * 이체 상태 (대기, 성공, 실패)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;
    
    /**
     * 실패 사유
     */
    @Column(length = 500)
    private String failureReason;
    
    /**
     * 이체 일시
     */
    @Column(nullable = false)
    private LocalDateTime transferDate;
    
    /**
     * 생성 일시
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * 이체 상태 업데이트
     */
    public void updateStatus(TransferStatus status, String failureReason) {
        this.status = status;
        this.failureReason = failureReason;
    }
}
