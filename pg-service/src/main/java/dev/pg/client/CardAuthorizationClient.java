package dev.pg.client;

import dev.pg.client.support.CardAuthorizationClientException;
import dev.pg.client.support.CardAuthorizationErrorType;
import dev.pg.client.support.ExternalErrorTranslator;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import org.springframework.stereotype.Component;

@Component
public class CardAuthorizationClient {

    private final CardAuthorizationServiceClient serviceClient;
    private final ExternalErrorTranslator externalErrorTranslator;

    public CardAuthorizationClient(
            CardAuthorizationServiceClient serviceClient,
            ExternalErrorTranslator externalErrorTranslator
    ) {
        this.serviceClient = serviceClient;
        this.externalErrorTranslator = externalErrorTranslator;
    }

    public CardAuthorizationResponse authorize(CardAuthorizationRequest request) {
        try {
            CardAuthorizationResponse response = serviceClient.authorize(request);

            if (response == null) {
                throw new CardAuthorizationClientException(
                        CardAuthorizationErrorType.EMPTY_RESPONSE,
                        "Empty response from card authorization service"
                );
            }

            return response;
        } catch (Exception e) {
            if (e instanceof CardAuthorizationClientException clientException) {
                throw clientException;
            }
            throw externalErrorTranslator.translateCardAuthorizationError(e);
        }
    }
}
