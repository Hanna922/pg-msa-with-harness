package com.card.payment.authorization.controller;

import com.card.payment.authorization.dto.AuthorizationRequest;
import com.card.payment.authorization.dto.AuthorizationResponse;
import com.card.payment.authorization.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 승인 컨트롤러
 * 카드 승인 요청을 수신하고 처리 결과를 반환합니다.
 */
@RestController
@RequestMapping("/api/authorization")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authorization", description = "카드 승인 API")
public class AuthorizationController {
    
    private final AuthorizationService authorizationService;
    
    /**
     * 승인 요청 처리
     * VAN으로부터 승인 요청을 수신하여 카드 유효성을 검증하고 승인/거절을 판단합니다.
     * 
     * @param request 승인 요청 DTO
     * @return 승인 응답 DTO
     */
    @Operation(
        summary = "카드 승인 요청",
        description = "카드 결제 승인을 요청합니다. 카드 유효성 검증 후 체크카드는 은행 잔액을 확인하고, 신용카드는 한도를 확인합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "승인 처리 완료 (승인 또는 거절)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AuthorizationResponse.class),
                examples = {
                    @ExampleObject(
                        name = "1. 체크카드 승인 성공",
                        description = "잔액이 충분한 체크카드 승인",
                        value = """
                            {
                              "transactionId": "TXN-CP-SUCCESS-001",
                              "cardNumber": "4111111111111111",
                              "amount": 10000,
                              "merchantId": "MERCHANT-001",
                              "pin": "1234"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "2. 체크카드 잔액 부족 거절",
                        description = "잔액이 부족한 체크카드 거절 (응답 코드: 51)",
                        value = """
                            {
                              "transactionId": "TXN-CP-INSUFFICIENT-001",
                              "cardNumber": "5555555555554444",
                              "amount": 600000,
                              "merchantId": "MERCHANT-001",
                              "pin": "1234"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "3. 신용카드 승인 성공",
                        description = "한도가 충분한 신용카드 승인",
                        value = """
                            {
                              "transactionId": "TXN-CREDIT-SUCCESS-001",
                              "cardNumber": "6011111111111117",
                              "amount": 100000,
                              "merchantId": "MERCHANT-001",
                              "pin": "1234"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "4. 신용카드 한도 초과 거절",
                        description = "한도를 초과한 신용카드 거절 (응답 코드: 61)",
                        value = """
                            {
                              "transactionId": "TXN-CREDIT-OVERLIMIT-001",
                              "cardNumber": "3530111333300000",
                              "amount": 600000,
                              "merchantId": "MERCHANT-001",
                              "pin": "1234"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "5. 카드 정지 거절",
                        description = "정지된 카드 거절 (응답 코드: 14)",
                        value = """
                            {
                              "transactionId": "TXN-SUSPENDED-001",
                              "cardNumber": "5105105105105100",
                              "amount": 10000,
                              "merchantId": "MERCHANT-001",
                              "pin": "1234"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "6. 유효기간 만료 거절",
                        description = "유효기간이 만료된 카드 거절 (응답 코드: 54)",
                        value = """
                            {
                              "transactionId": "TXN-EXPIRED-001",
                              "cardNumber": "4012888888881881",
                              "amount": 10000,
                              "merchantId": "MERCHANT-001",
                              "pin": "1234"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "7. 잘못된 PIN 거절",
                        description = "PIN이 일치하지 않는 경우 거절 (응답 코드: 55)",
                        value = """
                            {
                              "transactionId": "TXN-WRONG-PIN-001",
                              "cardNumber": "4111111111111111",
                              "amount": 10000,
                              "merchantId": "MERCHANT-001",
                              "pin": "9999"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "8. 필수 필드 누락",
                        description = "카드 번호 누락 시 오류 (응답 코드: 96)",
                        value = """
                            {
                              "transactionId": "TXN-INVALID-001",
                              "amount": 10000,
                              "merchantId": "MERCHANT-001"
                            }
                            """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (필수 필드 누락 또는 유효하지 않은 값)"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류"
        )
    })
    @PostMapping("/request")
    public ResponseEntity<AuthorizationResponse> processAuthorization(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "승인 요청 정보",
                required = true,
                content = @Content(
                    schema = @Schema(implementation = AuthorizationRequest.class)
                )
            )
            @Valid @RequestBody AuthorizationRequest request) {
        
        log.info("승인 요청 수신 - 거래 ID: {}, 가맹점 ID: {}, 금액: {}", 
                request.getTransactionId(), request.getMerchantId(), request.getAmount());
        
        try {
            // 입력 데이터 검증
            validateRequest(request);
            
            // 승인 처리
            AuthorizationResponse response = authorizationService.authorize(request);
            
            // 응답 반환
            if (response.isApproved()) {
                log.info("승인 성공 - 거래 ID: {}, 승인 번호: {}", 
                        request.getTransactionId(), response.getApprovalNumber());
                return ResponseEntity.ok(response);
            } else {
                log.warn("승인 거절 - 거래 ID: {}, 응답 코드: {}, 메시지: {}", 
                        request.getTransactionId(), response.getResponseCode(), response.getMessage());
                return ResponseEntity.ok(response);
            }
            
        } catch (IllegalArgumentException e) {
            log.error("잘못된 요청 - 거래 ID: {}, 오류: {}", request.getTransactionId(), e.getMessage());
            
            AuthorizationResponse errorResponse = AuthorizationResponse.builder()
                    .transactionId(request.getTransactionId())
                    .responseCode("96")
                    .message("잘못된 요청: " + e.getMessage())
                    .amount(request.getAmount())
                    .approved(false)
                    .build();
            
            return ResponseEntity.badRequest().body(errorResponse);
            
        } catch (Exception e) {
            log.error("승인 처리 중 예외 발생 - 거래 ID: {}", request.getTransactionId(), e);
            
            AuthorizationResponse errorResponse = AuthorizationResponse.builder()
                    .transactionId(request.getTransactionId())
                    .responseCode("96")
                    .message("시스템 오류: " + e.getMessage())
                    .amount(request.getAmount())
                    .approved(false)
                    .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * 요청 데이터 유효성 검증
     * 필수 필드와 데이터 형식을 검증합니다.
     * 
     * @param request 승인 요청
     * @throws IllegalArgumentException 유효성 검증 실패 시
     */
    private void validateRequest(AuthorizationRequest request) {
        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("거래 ID는 필수입니다.");
        }
        
        if (request.getCardNumber() == null || request.getCardNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("카드 번호는 필수입니다.");
        }
        
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("거래 금액은 양수여야 합니다.");
        }
        
        if (request.getMerchantId() == null || request.getMerchantId().trim().isEmpty()) {
            throw new IllegalArgumentException("가맹점 ID는 필수입니다.");
        }
        
        log.debug("요청 데이터 유효성 검증 완료 - 거래 ID: {}", request.getTransactionId());
    }
    
    /**
     * 예외 처리 핸들러
     * 컨트롤러에서 발생하는 예외를 처리합니다.
     * 
     * @param e 예외
     * @return 에러 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthorizationResponse> handleException(Exception e) {
        log.error("예외 발생", e);
        
        AuthorizationResponse errorResponse = AuthorizationResponse.builder()
                .responseCode("96")
                .message("시스템 오류: " + e.getMessage())
                .approved(false)
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
