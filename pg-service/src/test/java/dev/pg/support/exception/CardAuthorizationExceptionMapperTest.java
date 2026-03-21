package dev.pg.support.exception;

import dev.pg.client.support.CardAuthorizationClientException;
import dev.pg.client.support.CardAuthorizationErrorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardAuthorizationExceptionMapperTest {

    private final CardAuthorizationExceptionMapper mapper = new CardAuthorizationExceptionMapper();

    @Test
    void shouldMapCommunicationFailureToBusinessException() {
        BusinessException exception = mapper.toBusinessException(new CardAuthorizationClientException(
                CardAuthorizationErrorType.COMMUNICATION_FAILURE,
                "Card authorization service communication failed"
        ));

        assertEquals(ErrorCode.CARD_AUTH_COMMUNICATION_FAILURE, exception.getErrorCode());
        assertEquals("Card authorization service communication failed", exception.getMessage());
    }

    @Test
    void shouldMapDownstreamFailureToBusinessException() {
        BusinessException exception = mapper.toBusinessException(new CardAuthorizationClientException(
                CardAuthorizationErrorType.DOWNSTREAM_FAILURE,
                "Card authorization service returned HTTP 503"
        ));

        assertEquals(ErrorCode.CARD_AUTH_DOWNSTREAM_FAILURE, exception.getErrorCode());
        assertEquals("Card authorization service returned HTTP 503", exception.getMessage());
    }

    @Test
    void shouldMapCircuitOpenToBusinessException() {
        BusinessException exception = mapper.toBusinessException(new CardAuthorizationClientException(
                CardAuthorizationErrorType.CIRCUIT_OPEN,
                "Card authorization circuit breaker is open"
        ));

        assertEquals(ErrorCode.CARD_AUTH_CIRCUIT_OPEN, exception.getErrorCode());
        assertEquals("Card authorization circuit breaker is open", exception.getMessage());
    }
}
