package dev.pg.support.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorCodeTest {

    @Test
    void shouldExposeInvalidRequestMetadata() {
        assertEquals("PG-400", ErrorCode.INVALID_REQUEST.getCode());
        assertEquals("Invalid approval request", ErrorCode.INVALID_REQUEST.getDefaultMessage());
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST.getHttpStatus());
    }

    @Test
    void shouldExposeCardAuthorizationFailureMetadata() {
        assertEquals("PG-503", ErrorCode.CARD_AUTH_COMMUNICATION_FAILURE.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.CARD_AUTH_COMMUNICATION_FAILURE.getHttpStatus());
        assertEquals("PG-502", ErrorCode.CARD_AUTH_DOWNSTREAM_FAILURE.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ErrorCode.CARD_AUTH_DOWNSTREAM_FAILURE.getHttpStatus());
        assertEquals("PG-429", ErrorCode.CARD_AUTH_CIRCUIT_OPEN.getCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.CARD_AUTH_CIRCUIT_OPEN.getHttpStatus());
    }

    @Test
    void shouldExposeInternalErrorMetadata() {
        assertEquals("PG-500", ErrorCode.INTERNAL_ERROR.getCode());
        assertEquals("PG approval processing failed", ErrorCode.INTERNAL_ERROR.getDefaultMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.getHttpStatus());
    }
}
