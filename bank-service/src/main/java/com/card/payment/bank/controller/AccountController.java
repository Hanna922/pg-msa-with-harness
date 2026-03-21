package com.card.payment.bank.controller;

import com.card.payment.bank.dto.BalanceRequest;
import com.card.payment.bank.dto.BalanceResponse;
import com.card.payment.bank.dto.DebitRequest;
import com.card.payment.bank.dto.DebitResponse;
import com.card.payment.bank.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계좌 컨트롤러
 * 잔액 조회 및 출금 처리 엔드포인트 제공
 * 요구사항: 4.2, 4.6
 */
@Tag(name = "계좌 관리", description = "계좌 잔액 조회 및 출금 처리 API")
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Slf4j
public class AccountController {
    
    private final AccountService accountService;
    
    /**
     * 잔액 조회 엔드포인트
     * 카드 번호로 연결된 계좌의 잔액을 조회하고 출금 가능 여부를 확인
     * 요구사항: 4.2
     * 
     * @param request 잔액 조회 요청 (카드 번호, 요청 금액)
     * @return 잔액 조회 응답 (계좌 정보, 잔액, 출금 가능 여부)
     */
    @Operation(
        summary = "계좌 잔액 조회",
        description = "카드 번호로 연결된 계좌의 잔액을 조회하고 요청 금액에 대한 출금 가능 여부를 확인합니다. " +
                     "체크카드 승인 시 카드사가 호출하는 API입니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "잔액 조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BalanceResponse.class),
                examples = @ExampleObject(
                    name = "잔액 충분",
                    value = """
                        {
                          "accountNumber": "1234567890",
                          "balance": 1000000,
                          "minimumBalance": 0,
                          "availableBalance": 1000000,
                          "requestAmount": 50000,
                          "canWithdraw": true,
                          "responseCode": "00",
                          "responseMessage": "승인"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (필수 파라미터 누락 또는 유효하지 않은 값)",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                        {
                          "responseCode": "96",
                          "responseMessage": "카드 번호는 필수입니다"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "처리 불가 (계좌 상태 오류)",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                        {
                          "responseCode": "14",
                          "responseMessage": "계좌가 정지 상태입니다"
                        }
                        """
                )
            )
        )
    })
    @PostMapping("/balance")
    public ResponseEntity<BalanceResponse> checkBalance(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "잔액 조회 요청",
                required = true,
                content = @Content(
                    schema = @Schema(implementation = BalanceRequest.class),
                    examples = @ExampleObject(
                        value = """
                            {
                              "cardNumber": "1234567890123456",
                              "amount": 50000
                            }
                            """
                    )
                )
            )
            @RequestBody BalanceRequest request) {
        log.info("잔액 조회 요청 수신: cardNumber={}, amount={}", 
                request.getCardNumber(), request.getAmount());
        
        try {
            // 입력 검증
            if (request.getCardNumber() == null || request.getCardNumber().trim().isEmpty()) {
                log.warn("잔액 조회 실패: 카드 번호 누락");
                return ResponseEntity.badRequest().body(
                        BalanceResponse.builder()
                                .responseCode("96")
                                .responseMessage("카드 번호는 필수입니다")
                                .build()
                );
            }
            
            if (request.getAmount() == null || request.getAmount().signum() <= 0) {
                log.warn("잔액 조회 실패: 유효하지 않은 금액");
                return ResponseEntity.badRequest().body(
                        BalanceResponse.builder()
                                .responseCode("96")
                                .responseMessage("요청 금액은 0보다 커야 합니다")
                                .build()
                );
            }
            
            // 카드 번호로 계좌 조회
            var account = accountService.findAccountByCardNumber(request.getCardNumber());
            
            // 잔액 조회 (비관적 락 적용)
            var balanceResult = accountService.checkBalance(
                    account.getAccountNumber(), 
                    request.getAmount()
            );
            
            // 응답 생성
            BalanceResponse response = BalanceResponse.builder()
                    .accountNumber(balanceResult.getAccountNumber())
                    .balance(balanceResult.getBalance())
                    .minimumBalance(balanceResult.getMinimumBalance())
                    .availableBalance(balanceResult.getAvailableBalance())
                    .requestAmount(balanceResult.getRequestAmount())
                    .canWithdraw(balanceResult.isCanWithdraw())
                    .responseCode(balanceResult.isCanWithdraw() ? "00" : "51")
                    .responseMessage(balanceResult.isCanWithdraw() ? "승인" : "잔액 부족")
                    .build();
            
            log.info("잔액 조회 성공: accountNumber={}, canWithdraw={}", 
                    response.getAccountNumber(), response.isCanWithdraw());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("잔액 조회 실패 - 입력 오류: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    BalanceResponse.builder()
                            .responseCode("96")
                            .responseMessage(e.getMessage())
                            .build()
            );
            
        } catch (IllegalStateException e) {
            log.error("잔액 조회 실패 - 상태 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                    BalanceResponse.builder()
                            .responseCode("14")
                            .responseMessage(e.getMessage())
                            .build()
            );
            
        } catch (Exception e) {
            log.error("잔액 조회 실패 - 시스템 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    BalanceResponse.builder()
                            .responseCode("96")
                            .responseMessage("시스템 오류가 발생했습니다")
                            .build()
            );
        }
    }
    
    /**
     * 출금 처리 엔드포인트
     * 카드 번호로 연결된 계좌에서 금액을 출금
     * 요구사항: 4.6
     * 
     * @param request 출금 요청 (카드 번호, 출금 금액, 참조 ID)
     * @return 출금 응답 (거래 정보, 출금 후 잔액)
     */
    @Operation(
        summary = "계좌 출금 처리",
        description = "카드 번호로 연결된 계좌에서 지정된 금액을 출금합니다. " +
                     "체크카드 승인 시 카드사가 호출하는 API입니다. " +
                     "트랜잭션 처리를 통해 원자성을 보장합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "출금 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DebitResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "success": true,
                          "transactionId": "TXN20240214123456789",
                          "accountNumber": "1234567890",
                          "amount": 50000,
                          "balanceAfter": 950000,
                          "transactionDate": "2024-02-14T12:34:56",
                          "responseCode": "00",
                          "responseMessage": "출금 성공"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "잘못된 요청",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                        {
                          "success": false,
                          "responseCode": "96",
                          "responseMessage": "카드 번호는 필수입니다"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "처리 불가 (잔액 부족 또는 계좌 상태 오류)",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                        {
                          "success": false,
                          "responseCode": "51",
                          "responseMessage": "잔액 부족"
                        }
                        """
                )
            )
        )
    })
    @PostMapping("/debit")
    public ResponseEntity<DebitResponse> processDebit(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "출금 요청",
                required = true,
                content = @Content(
                    schema = @Schema(implementation = DebitRequest.class),
                    examples = @ExampleObject(
                        value = """
                            {
                              "cardNumber": "1234567890123456",
                              "amount": 50000,
                              "referenceId": "AUTH20240214123456"
                            }
                            """
                    )
                )
            )
            @RequestBody DebitRequest request) {
        log.info("출금 요청 수신: cardNumber={}, amount={}, referenceId={}", 
                request.getCardNumber(), request.getAmount(), request.getReferenceId());
        
        try {
            // 입력 검증
            if (request.getCardNumber() == null || request.getCardNumber().trim().isEmpty()) {
                log.warn("출금 실패: 카드 번호 누락");
                return ResponseEntity.badRequest().body(
                        DebitResponse.builder()
                                .success(false)
                                .responseCode("96")
                                .responseMessage("카드 번호는 필수입니다")
                                .build()
                );
            }
            
            if (request.getAmount() == null || request.getAmount().signum() <= 0) {
                log.warn("출금 실패: 유효하지 않은 금액");
                return ResponseEntity.badRequest().body(
                        DebitResponse.builder()
                                .success(false)
                                .responseCode("96")
                                .responseMessage("출금 금액은 0보다 커야 합니다")
                                .build()
                );
            }
            
            // 출금 처리
            var debitResult = accountService.processDebit(
                    request.getCardNumber(),
                    request.getAmount(),
                    request.getReferenceId()
            );
            
            // 응답 생성
            DebitResponse response = DebitResponse.builder()
                    .success(debitResult.isSuccess())
                    .transactionId(debitResult.getTransactionId())
                    .accountNumber(debitResult.getAccountNumber())
                    .amount(debitResult.getAmount())
                    .balanceAfter(debitResult.getBalanceAfter())
                    .transactionDate(debitResult.getTransactionDate())
                    .responseCode("00")
                    .responseMessage("출금 성공")
                    .build();
            
            log.info("출금 성공: transactionId={}, accountNumber={}, amount={}", 
                    response.getTransactionId(), response.getAccountNumber(), response.getAmount());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("출금 실패 - 입력 오류: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    DebitResponse.builder()
                            .success(false)
                            .responseCode("96")
                            .responseMessage(e.getMessage())
                            .build()
            );
            
        } catch (IllegalStateException e) {
            log.error("출금 실패 - 상태 오류: {}", e.getMessage());
            
            // 잔액 부족인 경우
            if (e.getMessage().contains("부족")) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                        DebitResponse.builder()
                                .success(false)
                                .responseCode("51")
                                .responseMessage("잔액 부족")
                                .build()
                );
            }
            
            // 기타 상태 오류
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                    DebitResponse.builder()
                            .success(false)
                            .responseCode("14")
                            .responseMessage(e.getMessage())
                            .build()
            );
            
        } catch (Exception e) {
            log.error("출금 실패 - 시스템 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    DebitResponse.builder()
                            .success(false)
                            .responseCode("96")
                            .responseMessage("시스템 오류가 발생했습니다")
                            .build()
            );
        }
    }
}
