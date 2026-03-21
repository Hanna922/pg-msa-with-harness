package dev.pg.client.support;

public class CardAuthorizationClientException extends RuntimeException {

    private final CardAuthorizationErrorType errorType;

    public CardAuthorizationClientException(CardAuthorizationErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public CardAuthorizationClientException(CardAuthorizationErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public CardAuthorizationErrorType getErrorType() {
        return errorType;
    }
}
