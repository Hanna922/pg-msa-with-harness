package com.card.payment.bank.repository;

import com.card.payment.bank.entity.CardAccountMapping;
import com.card.payment.common.enums.MappingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 카드-계좌 매핑 리포지토리
 * 요구사항: 4.2
 */
@Repository
public interface CardAccountMappingRepository extends JpaRepository<CardAccountMapping, Long> {
    
    /**
     * 카드 번호로 활성 상태의 매핑 조회
     */
    Optional<CardAccountMapping> findByCardNumberAndStatus(String cardNumber, MappingStatus status);
    
    /**
     * 계좌 번호로 활성 상태의 매핑 조회
     */
    Optional<CardAccountMapping> findByAccountNumberAndStatus(String accountNumber, MappingStatus status);
}
