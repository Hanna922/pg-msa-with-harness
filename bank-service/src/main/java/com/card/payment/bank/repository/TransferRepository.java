package com.card.payment.bank.repository;

import com.card.payment.bank.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 이체 내역 리포지토리
 * 요구사항: 11.4, 11.10
 */
@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {
    
    /**
     * 이체 고유 번호로 이체 조회
     */
    Optional<Transfer> findByTransferId(String transferId);
    
    /**
     * 정산 ID로 이체 조회
     */
    Optional<Transfer> findBySettlementId(String settlementId);
}
