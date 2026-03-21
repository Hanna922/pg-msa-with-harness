package com.card.payment.authorization.service;

import com.card.payment.authorization.client.BankClient;
import com.card.payment.authorization.dto.AuthorizationRequest;
import com.card.payment.authorization.dto.AuthorizationResponse;
import com.card.payment.authorization.dto.BalanceResponse;
import com.card.payment.authorization.dto.DebitResponse;
import com.card.payment.authorization.entity.Authorization;
import com.card.payment.authorization.entity.AuthorizationStatus;
import com.card.payment.authorization.entity.Card;
import com.card.payment.authorization.entity.CardType;
import com.card.payment.authorization.repository.AuthorizationRepository;
import com.card.payment.authorization.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * 승인 서비스
 * 카드 승인 요청을 처리하고 승인/거절을 판단합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {
    
    private final CardRepository cardRepository;
    private final AuthorizationRepository authorizationRepository;
    private final CardValidationService cardValidationService;
    private final BankClient bankClient;
    private final Random random = new Random();
    
    /**
     * 승인 요청 처리
     * 카드 타입에 따라 체크카드 또는 신용카드 승인을 처리합니다.
     * 
     * @param request 승인 요청
     * @return 승인 응답
     */
    @Transactional
    public AuthorizationResponse authorize(AuthorizationRequest request) {
        log.info("승인 요청 처리 시작 - 거래 ID: {}, 카드 번호: {}, 금액: {}", 
                request.getTransactionId(), maskCardNumber(request.getCardNumber()), request.getAmount());
        
        try {
            // 카드 유효성 검증
            Card card = cardValidationService.validateAndGetCard(request.getCardNumber());
            
            // PIN 검증 (요청에 PIN이 포함된 경우)
            if (request.getPin() != null && !request.getPin().isEmpty()) {
                CardValidationService.ValidationResult pinResult = cardValidationService.validatePin(card, request.getPin());
                if (!pinResult.isValid()) {
                    log.warn("PIN 검증 실패 - 거래 ID: {}, 응답 코드: {}", 
                            request.getTransactionId(), pinResult.getResponseCode());
                    return createRejectedResponse(request, pinResult.getResponseCode(), pinResult.getMessage());
                }
            }
            
            // 카드 타입 식별
            CardType cardType = identifyCardType(card);
            
            // 카드 타입에 따라 승인 처리
            AuthorizationResponse response;
            if (cardType == CardType.DEBIT) {
                response = authorizeDebitCard(request, card);
            } else {
                response = authorizeCreditCard(request, card);
            }
            
            log.info("승인 요청 처리 완료 - 거래 ID: {}, 응답 코드: {}, 승인 여부: {}", 
                    request.getTransactionId(), response.getResponseCode(), response.isApproved());
            
            return response;
            
        } catch (CardValidationService.CardValidationException e) {
            // 카드 유효성 검증 실패 시 거절 응답 반환
            log.warn("카드 유효성 검증 실패 - 거래 ID: {}, 응답 코드: {}, 메시지: {}", 
                    request.getTransactionId(), e.getResponseCode(), e.getMessage());
            return createRejectedResponse(request, e.getResponseCode(), e.getMessage());
        }
    }
    
    /**
     * 체크카드 승인 처리
     * 은행 잔액을 조회하고 충분한 경우 출금을 요청합니다.
     * 
     * @param request 승인 요청
     * @param card 카드 정보
     * @return 승인 응답
     */
    @Transactional
    public AuthorizationResponse authorizeDebitCard(AuthorizationRequest request, Card card) {
        log.info("체크카드 승인 처리 시작 - 거래 ID: {}", request.getTransactionId());
        
        try {
            // 1. 은행 잔액 조회
            log.debug("은행 잔액 조회 요청 - 카드 번호: {}, 금액: {}", 
                    maskCardNumber(request.getCardNumber()), request.getAmount());
            
            BalanceResponse balanceResponse = bankClient.checkBalance(
                    request.getCardNumber(), 
                    request.getAmount()
            );
            
            // 2. 잔액 부족 시 거절
            if (!balanceResponse.isSufficient()) {
                log.warn("잔액 부족으로 거절 - 거래 ID: {}, 요청 금액: {}, 사용 가능 금액: {}", 
                        request.getTransactionId(), request.getAmount(), balanceResponse.getAvailableAmount());
                
                return createRejectedResponse(request, "51", "잔액 부족");
            }
            
            // 3. 은행 출금 요청
            log.debug("은행 출금 요청 - 거래 ID: {}, 금액: {}", 
                    request.getTransactionId(), request.getAmount());
            
            DebitResponse debitResponse = bankClient.requestDebit(
                    request.getCardNumber(), 
                    request.getAmount(), 
                    request.getTransactionId()
            );
            
            // 4. 출금 실패 시 거절
            if (!debitResponse.isSuccess()) {
                log.error("출금 실패로 거절 - 거래 ID: {}, 메시지: {}", 
                        request.getTransactionId(), debitResponse.getMessage());
                
                return createRejectedResponse(request, "51", "출금 실패: " + debitResponse.getMessage());
            }
            
            // 5. 출금 성공 시 승인 번호 생성 및 승인 내역 저장
            String approvalNumber = generateApprovalNumber();
            log.info("출금 성공 - 거래 ID: {}, 승인 번호: {}", request.getTransactionId(), approvalNumber);
            
            return createApprovedResponse(request, approvalNumber);
            
        } catch (Exception e) {
            log.error("체크카드 승인 처리 중 오류 발생 - 거래 ID: {}", request.getTransactionId(), e);
            return createRejectedResponse(request, "96", "시스템 오류: " + e.getMessage());
        }
    }
    
    /**
     * 신용카드 승인 처리
     * 신용 한도를 조회하고 충분한 경우 한도를 차감합니다.
     * 
     * @param request 승인 요청
     * @param card 카드 정보 (유효성 검증 완료)
     * @return 승인 응답
     */
    @Transactional
    public AuthorizationResponse authorizeCreditCard(AuthorizationRequest request, Card card) {
        log.info("신용카드 승인 처리 시작 - 거래 ID: {}", request.getTransactionId());
        
        try {
            // 1. 신용 한도 조회 (비관적 락 적용)
            log.debug("신용 한도 조회 - 카드 번호: {}, 요청 금액: {}", 
                    maskCardNumber(request.getCardNumber()), request.getAmount());
            
            Card lockedCard = cardRepository.findByCardNumberWithLock(card.getCardNumber())
                    .orElseThrow(() -> new IllegalStateException("카드를 찾을 수 없습니다."));
            
            // 2. 사용 가능 한도 계산 (총 한도 - 사용 금액)
            BigDecimal availableLimit = lockedCard.getCreditLimit().subtract(lockedCard.getUsedAmount());
            
            log.debug("신용 한도 확인 - 총 한도: {}, 사용 금액: {}, 사용 가능 한도: {}", 
                    lockedCard.getCreditLimit(), lockedCard.getUsedAmount(), availableLimit);
            
            // 3. 한도 초과 시 거절 (응답 코드: 61)
            if (availableLimit.compareTo(request.getAmount()) < 0) {
                log.warn("한도 초과로 거절 - 거래 ID: {}, 요청 금액: {}, 사용 가능 한도: {}", 
                        request.getTransactionId(), request.getAmount(), availableLimit);
                
                return createRejectedResponse(request, "61", "한도 초과");
            }
            
            // 4. 한도 내 승인 시 사용 금액 증가 (트랜잭션)
            BigDecimal newUsedAmount = lockedCard.getUsedAmount().add(request.getAmount());
            lockedCard.setUsedAmount(newUsedAmount);
            cardRepository.save(lockedCard);
            
            log.info("신용 한도 차감 완료 - 거래 ID: {}, 차감 금액: {}, 새로운 사용 금액: {}", 
                    request.getTransactionId(), request.getAmount(), newUsedAmount);
            
            // 5. 승인 번호 생성 및 승인 내역 저장
            String approvalNumber = generateApprovalNumber();
            log.info("신용카드 승인 성공 - 거래 ID: {}, 승인 번호: {}", request.getTransactionId(), approvalNumber);
            
            return createApprovedResponse(request, approvalNumber);
            
        } catch (Exception e) {
            log.error("신용카드 승인 처리 중 오류 발생 - 거래 ID: {}", request.getTransactionId(), e);
            return createRejectedResponse(request, "96", "시스템 오류: " + e.getMessage());
        }
    }
    
    /**
     * 승인 응답 생성 및 저장
     * 
     * @param request 승인 요청
     * @param approvalNumber 승인 번호
     * @return 승인 응답
     */
    private AuthorizationResponse createApprovedResponse(AuthorizationRequest request, String approvalNumber) {
        LocalDateTime authorizationDate = LocalDateTime.now();
        
        // 승인 내역 저장
        Authorization authorization = Authorization.builder()
                .transactionId(request.getTransactionId())
                .cardNumberMasked(maskCardNumber(request.getCardNumber()))
                .amount(request.getAmount())
                .merchantId(request.getMerchantId())
                .approvalNumber(approvalNumber)
                .responseCode("00")
                .status(AuthorizationStatus.APPROVED)
                .authorizationDate(authorizationDate)
                .build();
        
        authorizationRepository.save(authorization);
        
        log.info("승인 내역 저장 완료 - 거래 ID: {}, 승인 번호: {}", request.getTransactionId(), approvalNumber);
        
        // 승인 응답 생성
        return AuthorizationResponse.builder()
                .transactionId(request.getTransactionId())
                .approvalNumber(approvalNumber)
                .responseCode("00")
                .message("승인")
                .amount(request.getAmount())
                .authorizationDate(authorizationDate)
                .approved(true)
                .build();
    }
    
    /**
     * 거절 응답 생성 및 저장
     * 
     * @param request 승인 요청
     * @param responseCode 응답 코드
     * @param message 거절 사유
     * @return 거절 응답
     */
    private AuthorizationResponse createRejectedResponse(AuthorizationRequest request, String responseCode, String message) {
        LocalDateTime authorizationDate = LocalDateTime.now();
        
        // 거절 내역 저장
        Authorization authorization = Authorization.builder()
                .transactionId(request.getTransactionId())
                .cardNumberMasked(maskCardNumber(request.getCardNumber()))
                .amount(request.getAmount())
                .merchantId(request.getMerchantId())
                .approvalNumber(null)
                .responseCode(responseCode)
                .status(AuthorizationStatus.REJECTED)
                .authorizationDate(authorizationDate)
                .build();
        
        authorizationRepository.save(authorization);
        
        log.info("거절 내역 저장 완료 - 거래 ID: {}, 응답 코드: {}", request.getTransactionId(), responseCode);
        
        // 거절 응답 생성
        return AuthorizationResponse.builder()
                .transactionId(request.getTransactionId())
                .approvalNumber(null)
                .responseCode(responseCode)
                .message(message)
                .amount(request.getAmount())
                .authorizationDate(authorizationDate)
                .approved(false)
                .build();
    }
    
    /**
     * 8자리 승인 번호 생성
     * 랜덤 숫자로 구성된 8자리 승인 번호를 생성합니다.
     * 
     * @return 승인 번호
     */
    private String generateApprovalNumber() {
        // 10000000 ~ 99999999 범위의 랜덤 숫자 생성
        int number = 10000000 + random.nextInt(90000000);
        return String.valueOf(number);
    }
    
    /**
     * 카드 타입 식별
     * 카드 정보로부터 카드 타입(CREDIT/DEBIT)을 식별합니다.
     * 
     * @param card 카드 정보
     * @return 카드 타입 (CREDIT 또는 DEBIT)
     */
    private CardType identifyCardType(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("카드 정보가 null입니다.");
        }
        
        CardType cardType = card.getCardType();
        log.debug("카드 타입 식별 - 카드 번호: {}, 타입: {}", 
                maskCardNumber(card.getCardNumber()), cardType);
        
        return cardType;
    }
    
    /**
     * 카드 번호 마스킹
     * 카드 번호를 마스킹 처리합니다 (예: 1234-****-****-5678)
     * 
     * @param cardNumber 카드 번호
     * @return 마스킹된 카드 번호
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 16) {
            return "****-****-****-****";
        }
        
        // 카드 번호에서 숫자만 추출
        String digitsOnly = cardNumber.replaceAll("[^0-9]", "");
        
        if (digitsOnly.length() < 16) {
            return "****-****-****-****";
        }
        
        // 앞 4자리와 뒤 4자리만 표시
        String first4 = digitsOnly.substring(0, 4);
        String last4 = digitsOnly.substring(digitsOnly.length() - 4);
        
        return first4 + "-****-****-" + last4;
    }
}
