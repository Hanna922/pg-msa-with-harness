package com.card.payment.authorization.repository;

import com.card.payment.authorization.entity.Authorization;
import com.card.payment.authorization.entity.AuthorizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 승인 리포지토리
 * 승인 내역에 대한 데이터베이스 접근을 담당합니다.
 */
@Repository
public interface AuthorizationRepository extends JpaRepository<Authorization, Long> {
    
    /**
     * 거래 ID로 승인 내역 조회
     * @param transactionId 거래 고유 번호
     * @return 승인 내역
     */
    Optional<Authorization> findByTransactionId(String transactionId);
    
    /**
     * 승인 번호로 승인 내역 조회
     * @param approvalNumber 승인 번호
     * @return 승인 내역
     */
    Optional<Authorization> findByApprovalNumber(String approvalNumber);
    
    /**
     * 가맹점 ID와 기간으로 승인 내역 조회
     * @param merchantId 가맹점 ID
     * @param startDate 시작 일시
     * @param endDate 종료 일시
     * @return 승인 내역 목록
     */
    @Query("SELECT a FROM Authorization a WHERE a.merchantId = :merchantId " +
           "AND a.authorizationDate BETWEEN :startDate AND :endDate")
    List<Authorization> findByMerchantIdAndDateRange(
        @Param("merchantId") String merchantId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * 승인 상태와 기간으로 승인 내역 조회
     * @param status 승인 상태
     * @param startDate 시작 일시
     * @param endDate 종료 일시
     * @return 승인 내역 목록
     */
    @Query("SELECT a FROM Authorization a WHERE a.status = :status " +
           "AND a.authorizationDate BETWEEN :startDate AND :endDate")
    List<Authorization> findByStatusAndDateRange(
        @Param("status") AuthorizationStatus status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
