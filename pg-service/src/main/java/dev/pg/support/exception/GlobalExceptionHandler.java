package dev.pg.support.exception;

import dev.pg.dto.MerchantApprovalResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<MerchantApprovalResponse> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(exception.getErrorCode().getHttpStatus())
                .body(buildErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MerchantApprovalResponse> handleUnexpectedException(Exception exception) {
        return ResponseEntity.internalServerError()
                .body(buildErrorResponse(ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }

    private MerchantApprovalResponse buildErrorResponse(String message) {
        return MerchantApprovalResponse.builder()
                .approved(false)
                .responseCode("96")
                .message(message)
                .approvedAt(LocalDateTime.now())
                .build();
    }
}
