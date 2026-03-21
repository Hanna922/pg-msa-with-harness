package dev.pg.support.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessExceptionTest {

    @Test
    void shouldUseErrorCodeDefaultMessage() {
        BusinessException exception = new BusinessException(ErrorCode.INTERNAL_ERROR);

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("PG approval processing failed", exception.getMessage());
    }

    @Test
    void shouldAllowCustomMessage() {
        BusinessException exception = new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "merchantTransactionId is required"
        );

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("merchantTransactionId is required", exception.getMessage());
    }
}
