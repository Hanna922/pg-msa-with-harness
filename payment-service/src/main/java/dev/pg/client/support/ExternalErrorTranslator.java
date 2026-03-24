package dev.pg.client.support;

import feign.FeignException;
import org.springframework.stereotype.Component;

@Component
public class ExternalErrorTranslator {

    public CardAuthorizationClientException translateCardAuthorizationError(Exception exception) {
        if (exception instanceof FeignException feignException) {
            return new CardAuthorizationClientException(
                    CardAuthorizationErrorType.DOWNSTREAM_FAILURE,
                    "Card authorization service returned HTTP " + feignException.status(),
                    feignException
            );
        }

        return new CardAuthorizationClientException(
                CardAuthorizationErrorType.COMMUNICATION_FAILURE,
                "Card authorization service communication failed",
                exception
        );
    }
}
