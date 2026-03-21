package com.card.payment.authorization.repository;

import com.card.payment.authorization.entity.Card;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 카드 리포지토리
 * 카드 정보에 대한 데이터베이스 접근을 담당합니다.
 */
@Repository
public interface CardRepository extends JpaRepository<Card, Long> {
    
    /**
     * 카드 번호로 카드 조회
     * @param cardNumber 카드 번호 (암호화된 값)
     * @return 카드 정보
     */
    Optional<Card> findByCardNumber(String cardNumber);
    
    /**
     * 카드 번호로 카드 조회 (비관적 락 적용)
     * 동시성 제어를 위해 신용카드 한도 조회 시 사용합니다.
     * @param cardNumber 카드 번호 (암호화된 값)
     * @return 카드 정보
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Card c WHERE c.cardNumber = :cardNumber")
    Optional<Card> findByCardNumberWithLock(@Param("cardNumber") String cardNumber);
    
    /**
     * 고객 ID로 카드 목록 조회
     * @param customerId 고객 ID
     * @return 카드 목록
     */
    @Query("SELECT c FROM Card c WHERE c.customerId = :customerId")
    List<Card> findByCustomerId(@Param("customerId") String customerId);
}
