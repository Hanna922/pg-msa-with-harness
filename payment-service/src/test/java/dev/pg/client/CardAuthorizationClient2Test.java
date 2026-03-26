package dev.pg.client;

import dev.pg.client.support.CardAuthorizationClientException;
import dev.pg.client.support.CardAuthorizationErrorType;
import dev.pg.client.support.ExternalErrorTranslator;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardAuthorizationClient2Test {

    private final CardAuthorizationServiceClient2 serviceClient = mock(CardAuthorizationServiceClient2.class);
    private final ExternalErrorTranslator externalErrorTranslator = new ExternalErrorTranslator();
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory = mock(CircuitBreakerFactory.class);
    private final CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
    private final CardAuthorizationClient2 client;

    CardAuthorizationClient2Test() {
        when(circuitBreakerFactory.create("cardAuthorization2")).thenReturn(circuitBreaker);
        doAnswer(invocation -> {
            Supplier<CardAuthorizationResponse> supplier = invocation.getArgument(0);
            Function<Throwable, CardAuthorizationResponse> fallback = invocation.getArgument(1);
            try {
                return supplier.get();
            } catch (Throwable throwable) {
                return fallback.apply(throwable);
            }
        }).when(circuitBreaker).run(any(), any());
        client = new CardAuthorizationClient2(serviceClient, externalErrorTranslator, circuitBreakerFactory, 2);
    }

    @Test
    void shouldReturnResponseWhenAuthorizationSucceeds() {
        CardAuthorizationRequest request = CardAuthorizationRequest.builder()
                .transactionId("PG20260321200000ABCDEF")
                .cardNumber("5555555555554444")
                .amount(new BigDecimal("10000"))
                .merchantId("MERCHANT-001")
                .build();
        CardAuthorizationResponse response = CardAuthorizationResponse.builder()
                .transactionId("PG20260321200000ABCDEF")
                .approvalNumber("22345678")
                .responseCode("00")
                .message("Approved")
                .amount(new BigDecimal("10000"))
                .approved(true)
                .build();

        when(serviceClient.authorize(request)).thenReturn(response);

        CardAuthorizationResponse actual = client.authorize(request);

        assertEquals("00", actual.getResponseCode());
        assertEquals("22345678", actual.getApprovalNumber());
    }

    @Test
    void shouldRetryCommunicationFailureAndSucceedOnSecondAttempt() {
        CardAuthorizationRequest request = CardAuthorizationRequest.builder()
                .transactionId("PG20260321200000ABCDEF")
                .cardNumber("5555555555554444")
                .amount(new BigDecimal("10000"))
                .merchantId("MERCHANT-001")
                .build();
        CardAuthorizationResponse response = CardAuthorizationResponse.builder()
                .transactionId("PG20260321200000ABCDEF")
                .approvalNumber("22345678")
                .responseCode("00")
                .message("Approved")
                .amount(new BigDecimal("10000"))
                .approved(true)
                .build();

        when(serviceClient.authorize(request))
                .thenThrow(new RuntimeException("socket closed"))
                .thenReturn(response);

        CardAuthorizationResponse actual = client.authorize(request);

        assertEquals("00", actual.getResponseCode());
        verify(serviceClient, times(2)).authorize(request);
    }

    @Test
    void shouldTranslateFeignExceptionToDownstreamFailure() {
        CardAuthorizationRequest request = CardAuthorizationRequest.builder()
                .transactionId("PG20260321200000ABCDEF")
                .cardNumber("5555555555554444")
                .amount(new BigDecimal("10000"))
                .merchantId("MERCHANT-001")
                .build();
        Request feignRequest = Request.create(
                Request.HttpMethod.POST,
                "/api/authorization/request",
                Collections.emptyMap(),
                null,
                new RequestTemplate()
        );
        FeignException feignException = FeignException.errorStatus(
                "authorize",
                feign.Response.builder()
                        .status(503)
                        .reason("Service Unavailable")
                        .request(feignRequest)
                        .headers(Collections.emptyMap())
                        .body("unavailable", StandardCharsets.UTF_8)
                        .build()
        );

        when(serviceClient.authorize(request)).thenThrow(feignException);

        CardAuthorizationClientException exception = assertThrows(
                CardAuthorizationClientException.class,
                () -> client.authorize(request)
        );

        assertEquals(CardAuthorizationErrorType.DOWNSTREAM_FAILURE, exception.getErrorType());
    }

    @Test
    void shouldOpenCircuitBreakerIndependently() {
        CardAuthorizationRequest request = CardAuthorizationRequest.builder()
                .transactionId("PG20260321200000ABCDEF")
                .cardNumber("5555555555554444")
                .amount(new BigDecimal("10000"))
                .merchantId("MERCHANT-001")
                .build();

        doAnswer(invocation -> {
            Function<Throwable, CardAuthorizationResponse> fallback = invocation.getArgument(1);
            return fallback.apply(CallNotPermittedException.createCallNotPermittedException(
                    io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("cardAuthorization2")
            ));
        }).when(circuitBreaker).run(any(), any());

        CardAuthorizationClientException exception = assertThrows(
                CardAuthorizationClientException.class,
                () -> client.authorize(request)
        );

        assertEquals(CardAuthorizationErrorType.CIRCUIT_OPEN, exception.getErrorType());
        assertEquals("Card authorization service 2 circuit breaker is open", exception.getMessage());
        verify(serviceClient, times(0)).authorize(request);
    }
}
