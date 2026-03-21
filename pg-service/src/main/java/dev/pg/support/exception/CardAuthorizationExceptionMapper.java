package dev.pg.support.exception;

import dev.pg.client.support.CardAuthorizationClientException;
import dev.pg.client.support.CardAuthorizationErrorType;
import org.springframework.stereotype.Component;

@Component
public class CardAuthorizationExceptionMapper {

    public BusinessException toBusinessException(CardAuthorizationClientException exception) {
        return new BusinessException(resolveErrorCode(exception.getErrorType()), exception.getMessage(), exception);
    }

    private ErrorCode resolveErrorCode(CardAuthorizationErrorType errorType) {
        return switch (errorType) {
            case COMMUNICATION_FAILURE -> ErrorCode.CARD_AUTH_COMMUNICATION_FAILURE;
            case DOWNSTREAM_FAILURE, EMPTY_RESPONSE -> ErrorCode.CARD_AUTH_DOWNSTREAM_FAILURE;
            case CIRCUIT_OPEN -> ErrorCode.CARD_AUTH_CIRCUIT_OPEN;
        };
    }
}
