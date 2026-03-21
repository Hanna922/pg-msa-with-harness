package dev.pg.client;

import dev.pg.client.support.CardAuthorizationClientException;
import dev.pg.client.support.CardAuthorizationErrorType;
import dev.pg.client.support.ExternalErrorTranslator;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

@Component
public class CardAuthorizationClient2 {

    private final CardAuthorizationServiceClient2 serviceClient;
    private final ExternalErrorTranslator externalErrorTranslator;
    private final CircuitBreaker circuitBreaker;
    private final int maxAttempts;

    public CardAuthorizationClient2(
            CardAuthorizationServiceClient2 serviceClient,
            ExternalErrorTranslator externalErrorTranslator,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            @Value("${pg.client.card-authorization-2.retry.max-attempts:2}") int maxAttempts
    ) {
        this.serviceClient = serviceClient;
        this.externalErrorTranslator = externalErrorTranslator;
        this.circuitBreaker = circuitBreakerFactory.create("cardAuthorization2");
        this.maxAttempts = maxAttempts;
    }

    public CardAuthorizationResponse authorize(CardAuthorizationRequest request) {
        return circuitBreaker.run(
                () -> authorizeWithRetry(request),
                this::handleCircuitBreakerFailure
        );
    }

    private CardAuthorizationResponse authorizeWithRetry(CardAuthorizationRequest request) {
        CardAuthorizationClientException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                CardAuthorizationResponse response = serviceClient.authorize(request);

                if (response == null) {
                    throw new CardAuthorizationClientException(
                            CardAuthorizationErrorType.EMPTY_RESPONSE,
                            "Empty response from card authorization service 2"
                    );
                }

                return response;
            } catch (Exception e) {
                CardAuthorizationClientException translatedException =
                        (e instanceof CardAuthorizationClientException clientException)
                                ? clientException
                                : externalErrorTranslator.translateCardAuthorizationError(e);

                if (!shouldRetry(translatedException, attempt)) {
                    throw translatedException;
                }
                lastException = translatedException;
            }
        }

        throw lastException != null
                ? lastException
                : new CardAuthorizationClientException(
                CardAuthorizationErrorType.COMMUNICATION_FAILURE,
                "Card authorization service 2 communication failed"
        );
    }

    private boolean shouldRetry(CardAuthorizationClientException exception, int attempt) {
        return exception.getErrorType() == CardAuthorizationErrorType.COMMUNICATION_FAILURE
                && attempt < maxAttempts;
    }

    private CardAuthorizationResponse handleCircuitBreakerFailure(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            throw new CardAuthorizationClientException(
                    CardAuthorizationErrorType.CIRCUIT_OPEN,
                    "Card authorization service 2 circuit breaker is open",
                    throwable
            );
        }

        if (throwable instanceof CardAuthorizationClientException clientException) {
            throw clientException;
        }

        if (throwable instanceof Exception exception) {
            throw externalErrorTranslator.translateCardAuthorizationError(exception);
        }

        throw new CardAuthorizationClientException(
                CardAuthorizationErrorType.COMMUNICATION_FAILURE,
                "Card authorization service 2 communication failed",
                new RuntimeException(throwable)
        );
    }
}
