package dev.pg.support.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST("PG-400", "Invalid approval request", HttpStatus.BAD_REQUEST),
    CARD_AUTH_COMMUNICATION_FAILURE("PG-503", "Card authorization communication failed", HttpStatus.SERVICE_UNAVAILABLE),
    CARD_AUTH_DOWNSTREAM_FAILURE("PG-502", "Card authorization downstream failure", HttpStatus.BAD_GATEWAY),
    CARD_AUTH_CIRCUIT_OPEN("PG-429", "Card authorization circuit breaker is open", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR("PG-500", "PG approval processing failed", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
