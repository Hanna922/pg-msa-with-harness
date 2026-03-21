package com.card.payment.bank.controller;

import com.card.payment.bank.dto.TransferRequest;
import com.card.payment.bank.dto.TransferResponse;
import com.card.payment.bank.service.TransferService;
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
 * 이체 컨트롤러
 * 계좌 간 이체 처리 엔드포인트 제공
 * 요구사항: 11.3, 11.11
 */
@Tag(name = "이체 관리", description = "계좌 간 이체 처리 API")
@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
@Slf4j
public class TransferController {
    
    private final TransferService transferService;
    
    /**
     * 이체 처리 엔드포인트
     * 출금 계좌에서 입금 계좌로 금액을 이체
     * 요구사항: 11.3, 11.11
     * 
     * @param request 이체 요청 (출금 계좌, 입금 계좌, 이체 금액, 정산 ID)
     * @return 이체 응답 (이체 정보, 성공 여부)
     */
    @Operation(
        summary = "계좌 이체 처리",
        description = "출금 계좌에서 입금 계좌로 지정된 금액을 이체합니다. " +
                     "카드사의 정산 서비스가 가맹점에게 정산 금액을 지급할 때 호출하는 API입니다. " +
                     "출금과 입금을 하나의 트랜잭션으로 처리하여 원자성을 보장합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "이체 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = TransferResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "success": true,
                          "transferId": "TRF20240214123456789",
                          "fromAccount": "9999999999",
                          "toAccount": "1234567890",
                          "amount": 950000,
                          "transferDate": "2024-02-14T12:34:56",
                          "responseCode": "00",
                          "responseMessage": "이체 성공",
                          "failureReason": null
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
                          "responseMessage": "출금 계좌 번호는 필수입니다"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "처리 불가 (잔액 부족, 계좌 상태 오류 등)",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                        {
                          "success": false,
                          "transferId": "TRF20240214123456789",
                          "responseCode": "96",
                          "responseMessage": "이체 실패",
                          "failureReason": "출금 계좌의 잔액이 부족합니다"
                        }
                        """
                )
            )
        )
    })
    @PostMapping("/request")
    public ResponseEntity<TransferResponse> processTransfer(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "이체 요청",
                required = true,
                content = @Content(
                    schema = @Schema(implementation = TransferRequest.class),
                    examples = @ExampleObject(
                        value = """
                            {
                              "settlementId": "SETTLE20240214001",
                              "fromAccount": "9999999999",
                              "toAccount": "1234567890",
                              "amount": 950000
                            }
                            """
                    )
                )
            )
            @RequestBody TransferRequest request) {
        log.info("이체 요청 수신: fromAccount={}, toAccount={}, amount={}, settlementId={}", 
                request.getFromAccount(), request.getToAccount(), 
                request.getAmount(), request.getSettlementId());
        
        try {
            // 입력 검증
            if (request.getFromAccount() == null || request.getFromAccount().trim().isEmpty()) {
                log.warn("이체 실패: 출금 계좌 번호 누락");
                return ResponseEntity.badRequest().body(
                        TransferResponse.builder()
                                .success(false)
                                .responseCode("96")
                                .responseMessage("출금 계좌 번호는 필수입니다")
                                .build()
                );
            }
            
            if (request.getToAccount() == null || request.getToAccount().trim().isEmpty()) {
                log.warn("이체 실패: 입금 계좌 번호 누락");
                return ResponseEntity.badRequest().body(
                        TransferResponse.builder()
                                .success(false)
                                .responseCode("96")
                                .responseMessage("입금 계좌 번호는 필수입니다")
                                .build()
                );
            }
            
            if (request.getAmount() == null || request.getAmount().signum() <= 0) {
                log.warn("이체 실패: 유효하지 않은 금액");
                return ResponseEntity.badRequest().body(
                        TransferResponse.builder()
                                .success(false)
                                .responseCode("96")
                                .responseMessage("이체 금액은 0보다 커야 합니다")
                                .build()
                );
            }
            
            // 동일 계좌 이체 방지
            if (request.getFromAccount().equals(request.getToAccount())) {
                log.warn("이체 실패: 동일 계좌 이체 시도");
                return ResponseEntity.badRequest().body(
                        TransferResponse.builder()
                                .success(false)
                                .responseCode("96")
                                .responseMessage("출금 계좌와 입금 계좌가 동일할 수 없습니다")
                                .build()
                );
            }
            
            // 이체 처리
            var transferResult = transferService.processTransfer(
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    request.getSettlementId()
            );
            
            // 응답 생성
            if (transferResult.isSuccess()) {
                TransferResponse response = TransferResponse.builder()
                        .success(true)
                        .transferId(transferResult.getTransferId())
                        .fromAccount(transferResult.getFromAccount())
                        .toAccount(transferResult.getToAccount())
                        .amount(transferResult.getAmount())
                        .transferDate(transferResult.getTransferDate())
                        .responseCode("00")
                        .responseMessage("이체 성공")
                        .failureReason(null)
                        .build();
                
                log.info("이체 성공: transferId={}, fromAccount={}, toAccount={}, amount={}", 
                        response.getTransferId(), response.getFromAccount(), 
                        response.getToAccount(), response.getAmount());
                
                return ResponseEntity.ok(response);
                
            } else {
                TransferResponse response = TransferResponse.builder()
                        .success(false)
                        .transferId(transferResult.getTransferId())
                        .fromAccount(null)
                        .toAccount(null)
                        .amount(null)
                        .transferDate(null)
                        .responseCode("96")
                        .responseMessage("이체 실패")
                        .failureReason(transferResult.getFailureReason())
                        .build();
                
                log.error("이체 실패: transferId={}, failureReason={}", 
                        response.getTransferId(), response.getFailureReason());
                
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
            }
            
        } catch (IllegalArgumentException e) {
            log.error("이체 실패 - 입력 오류: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    TransferResponse.builder()
                            .success(false)
                            .responseCode("96")
                            .responseMessage(e.getMessage())
                            .build()
            );
            
        } catch (IllegalStateException e) {
            log.error("이체 실패 - 상태 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                    TransferResponse.builder()
                            .success(false)
                            .responseCode("14")
                            .responseMessage(e.getMessage())
                            .failureReason(e.getMessage())
                            .build()
            );
            
        } catch (Exception e) {
            log.error("이체 실패 - 시스템 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    TransferResponse.builder()
                            .success(false)
                            .responseCode("96")
                            .responseMessage("시스템 오류가 발생했습니다")
                            .failureReason(e.getMessage())
                            .build()
            );
        }
    }
}
