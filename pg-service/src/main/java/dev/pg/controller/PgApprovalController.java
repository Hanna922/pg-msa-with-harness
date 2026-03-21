package dev.pg.controller;

import dev.pg.approval.service.PgApprovalFacade;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/pg")
public class PgApprovalController {

    private final PgApprovalFacade pgApprovalFacade;
    private static final String GENERIC_ERROR_MESSAGE = "PG approval processing failed";

    public PgApprovalController(PgApprovalFacade pgApprovalFacade) {
        this.pgApprovalFacade = pgApprovalFacade;
    }

    @PostMapping("/approve")
    public ResponseEntity<MerchantApprovalResponse> approve(@RequestBody MerchantApprovalRequest request) {
        return ResponseEntity.ok(pgApprovalFacade.approve(request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MerchantApprovalResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(buildErrorResponse(null, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MerchantApprovalResponse> handleUnexpectedException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse(null, GENERIC_ERROR_MESSAGE));
    }

    private MerchantApprovalResponse buildErrorResponse(String merchantTransactionId, String message) {
        return MerchantApprovalResponse.builder()
                .merchantTransactionId(merchantTransactionId)
                .approved(false)
                .responseCode("96")
                .message(message)
                .approvedAt(LocalDateTime.now())
                .build();
    }
}
