package com.card.payment.authorization.service;

import com.card.payment.authorization.entity.Card;
import com.card.payment.authorization.entity.CardStatus;
import com.card.payment.authorization.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 카드 유효성 검증 서비스
 * 카드 번호 형식, 카드 상태, 유효기간, PIN 등을 검증합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardValidationService {
    
    private final CardRepository cardRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    
    /**
     * 카드 유효성 검증 결과
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String responseCode;
        private final String message;
        
        private ValidationResult(boolean valid, String responseCode, String message) {
            this.valid = valid;
            this.responseCode = responseCode;
            this.message = message;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, "00", "승인");
        }
        
        public static ValidationResult failure(String responseCode, String message) {
            return new ValidationResult(false, responseCode, message);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getResponseCode() {
            return responseCode;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * 카드 번호 Luhn 알고리즘 검증
     * 카드 번호의 형식이 올바른지 검증합니다.
     * 
     * @param cardNumber 카드 번호 (숫자만, 하이픈 제거)
     * @return 검증 결과 (true: 유효, false: 무효)
     */
    public boolean validateLuhnAlgorithm(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            log.warn("카드 번호가 null 또는 빈 문자열입니다.");
            return false;
        }
        
        // 숫자가 아닌 문자 제거
        String cleanedCardNumber = cardNumber.replaceAll("\\D", "");
        
        // 카드 번호 길이 검증 (일반적으로 13-19자리)
        if (cleanedCardNumber.length() < 13 || cleanedCardNumber.length() > 19) {
            log.warn("카드 번호 길이가 유효하지 않습니다: {}", cleanedCardNumber.length());
            return false;
        }
        
        // Luhn 알고리즘 적용
        int sum = 0;
        boolean alternate = false;
        
        // 오른쪽에서 왼쪽으로 순회
        for (int i = cleanedCardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cleanedCardNumber.charAt(i));
            
            if (digit < 0 || digit > 9) {
                log.warn("카드 번호에 숫자가 아닌 문자가 포함되어 있습니다.");
                return false;
            }
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        boolean isValid = (sum % 10 == 0);
        log.debug("Luhn 알고리즘 검증 결과: {}", isValid);
        return isValid;
    }
    
    /**
     * 카드 상태 확인
     * 카드가 정상 상태인지 확인하고, 비정상 상태일 경우 적절한 응답 코드를 반환합니다.
     * 
     * @param card 카드 엔티티
     * @return 검증 결과 (정상: 00, 정지: 14, 분실/해지: 14)
     */
    public ValidationResult checkCardStatus(Card card) {
        if (card == null) {
            log.error("카드 정보가 null입니다.");
            return ValidationResult.failure("14", "카드 정보 없음");
        }
        
        CardStatus status = card.getCardStatus();
        
        switch (status) {
            case ACTIVE:
                log.debug("카드 상태 정상: {}", card.getCardNumber());
                return ValidationResult.success();
                
            case SUSPENDED:
                log.warn("카드 정지 상태: {}", card.getCardNumber());
                return ValidationResult.failure("14", "카드 정지");
                
            case LOST:
                log.warn("카드 분실 상태: {}", card.getCardNumber());
                return ValidationResult.failure("14", "카드 분실");
                
            case TERMINATED:
                log.warn("카드 해지 상태: {}", card.getCardNumber());
                return ValidationResult.failure("14", "카드 해지");
                
            default:
                log.error("알 수 없는 카드 상태: {}", status);
                return ValidationResult.failure("14", "알 수 없는 카드 상태");
        }
    }
    
    /**
     * 유효기간 검증
     * 카드의 유효기간이 만료되지 않았는지 확인합니다.
     * 
     * @param card 카드 엔티티
     * @return 검증 결과 (유효: 00, 만료: 54)
     */
    public ValidationResult validateExpiryDate(Card card) {
        if (card == null || card.getExpiryDate() == null) {
            log.error("카드 정보 또는 유효기간이 null입니다.");
            return ValidationResult.failure("54", "유효기간 정보 없음");
        }
        
        LocalDate expiryDate = card.getExpiryDate();
        LocalDate today = LocalDate.now();
        
        // 유효기간은 해당 월의 마지막 날까지 유효
        LocalDate expiryEndOfMonth = expiryDate.withDayOfMonth(expiryDate.lengthOfMonth());
        
        if (today.isAfter(expiryEndOfMonth)) {
            log.warn("카드 유효기간 만료: {} (현재: {})", expiryDate, today);
            return ValidationResult.failure("54", "유효기간 만료");
        }
        
        log.debug("카드 유효기간 정상: {} (현재: {})", expiryDate, today);
        return ValidationResult.success();
    }
    
    /**
     * PIN 검증
     * 입력된 PIN이 저장된 PIN과 일치하는지 BCrypt를 사용하여 검증합니다.
     * 
     * @param card 카드 엔티티
     * @param inputPin 입력된 PIN (평문)
     * @return 검증 결과 (일치: 00, 불일치: 55)
     */
    public ValidationResult validatePin(Card card, String inputPin) {
        if (card == null || card.getPin() == null) {
            log.error("카드 정보 또는 PIN이 null입니다.");
            return ValidationResult.failure("55", "PIN 정보 없음");
        }
        
        if (inputPin == null || inputPin.isEmpty()) {
            log.warn("입력된 PIN이 null 또는 빈 문자열입니다.");
            return ValidationResult.failure("55", "PIN 입력 오류");
        }
        
        // 디버깅: 입력값과 저장된 해시 로깅
        log.info("PIN 검증 시작 - 입력 PIN: [{}], 입력 PIN 길이: {}", inputPin, inputPin.length());
        log.info("저장된 PIN 해시: [{}], 해시 길이: {}", card.getPin(), card.getPin().length());
        log.info("저장된 PIN 해시 시작 문자: [{}]", card.getPin().substring(0, Math.min(10, card.getPin().length())));

        log.info("PIN 해시: {}", passwordEncoder.encode(inputPin));
        
        // BCrypt를 사용하여 PIN 검증
        boolean matches = passwordEncoder.matches(inputPin, card.getPin());
        
        log.info("BCrypt 검증 결과: {}", matches);
        
        if (!matches) {
            log.warn("PIN 불일치: 카드 번호 {}", card.getCardNumber());
            return ValidationResult.failure("55", "PIN 오류");
        }
        
        log.debug("PIN 검증 성공: 카드 번호 {}", card.getCardNumber());
        return ValidationResult.success();
    }
    
    /**
     * 카드 전체 유효성 검증
     * Luhn 알고리즘, 카드 상태, 유효기간을 순차적으로 검증합니다.
     * PIN 검증은 별도로 호출해야 합니다.
     * 
     * @param cardNumber 카드 번호 (암호화된 값)
     * @return 검증 결과
     */
    public ValidationResult validateCard(String cardNumber) {
        // 1. Luhn 알고리즘 검증
        if (!validateLuhnAlgorithm(cardNumber)) {
            log.warn("Luhn 알고리즘 검증 실패: {}", cardNumber);
            return ValidationResult.failure("14", "카드 번호 형식 오류");
        }
        
        // 2. 카드 조회
        Optional<Card> cardOptional = cardRepository.findByCardNumber(cardNumber);
        if (cardOptional.isEmpty()) {
            log.warn("카드를 찾을 수 없습니다: {}", cardNumber);
            return ValidationResult.failure("14", "카드 정보 없음");
        }
        
        Card card = cardOptional.get();
        
        // 3. 카드 상태 확인
        ValidationResult statusResult = checkCardStatus(card);
        if (!statusResult.isValid()) {
            return statusResult;
        }
        
        // 4. 유효기간 검증
        ValidationResult expiryResult = validateExpiryDate(card);
        if (!expiryResult.isValid()) {
            return expiryResult;
        }
        
        log.info("카드 유효성 검증 성공: {}", cardNumber);
        return ValidationResult.success();
    }
    
    /**
     * 카드 유효성 검증 및 카드 객체 반환
     * 카드 유효성을 검증하고, 검증에 성공하면 Card 객체를 반환합니다.
     * 검증 실패 시 예외를 발생시킵니다.
     * 
     * @param cardNumber 카드 번호 (암호화된 값)
     * @return 카드 엔티티
     * @throws CardValidationException 카드 유효성 검증 실패 시
     */
    public Card validateAndGetCard(String cardNumber) {
        // 1. Luhn 알고리즘 검증
        if (!validateLuhnAlgorithm(cardNumber)) {
            log.warn("Luhn 알고리즘 검증 실패: {}", cardNumber);
            throw new CardValidationException("14", "카드 번호 형식 오류");
        }
        
        // 2. 카드 조회
        Optional<Card> cardOptional = cardRepository.findByCardNumber(cardNumber);
        if (cardOptional.isEmpty()) {
            log.warn("카드를 찾을 수 없습니다: {}", cardNumber);
            throw new CardValidationException("14", "카드 정보 없음");
        }
        
        Card card = cardOptional.get();
        
        // 3. 카드 상태 확인
        ValidationResult statusResult = checkCardStatus(card);
        if (!statusResult.isValid()) {
            throw new CardValidationException(statusResult.getResponseCode(), statusResult.getMessage());
        }
        
        // 4. 유효기간 검증
        ValidationResult expiryResult = validateExpiryDate(card);
        if (!expiryResult.isValid()) {
            throw new CardValidationException(expiryResult.getResponseCode(), expiryResult.getMessage());
        }
        
        log.info("카드 유효성 검증 성공: {}", cardNumber);
        return card;
    }
    
    /**
     * 카드 유효성 검증 예외
     */
    public static class CardValidationException extends RuntimeException {
        private final String responseCode;
        
        public CardValidationException(String responseCode, String message) {
            super(message);
            this.responseCode = responseCode;
        }
        
        public String getResponseCode() {
            return responseCode;
        }
    }
}
