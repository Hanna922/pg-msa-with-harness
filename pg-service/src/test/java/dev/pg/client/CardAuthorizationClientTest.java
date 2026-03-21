package dev.pg.client;

import dev.pg.client.support.CardAuthorizationClientException;
import dev.pg.client.support.CardAuthorizationErrorType;
import dev.pg.client.support.ExternalErrorTranslator;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardAuthorizationClientTest {

    private final CardAuthorizationServiceClient serviceClient = mock(CardAuthorizationServiceClient.class);
    private final ExternalErrorTranslator externalErrorTranslator = new ExternalErrorTranslator();
    private final CardAuthorizationClient client =
            new CardAuthorizationClient(serviceClient, externalErrorTranslator, 2);

    @Test
    void shouldThrowTranslatedExceptionForEmptyResponse() {
        CardAuthorizationRequest request = CardAuthorizationRequest.builder()
                .transactionId("PG20260321200000ABCDEF")
                .cardNumber("4111111111111111")
                .amount(new BigDecimal("10000"))
                .merchantId("MERCHANT-001")
                .build();

        when(serviceClient.authorize(request)).thenReturn(null);

        CardAuthorizationClientException exception = assertThrows(
                CardAuthorizationClientException.class,
                () -> client.authorize(request)
        );

        assertEquals(CardAuthorizationErrorType.EMPTY_RESPONSE, exception.getErrorType());
        assertEquals("Empty response from card authorization service", exception.getMessage());
    }

    @Test
    void shouldTranslateFeignExceptionToDownstreamFailure() {
        CardAuthorizationRequest request = CardAuthorizationRequest.builder()
                .transactionId("PG20260321200000ABCDEF")
                .cardNumber("4111111111111111")
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
        assertEquals("Card authorization service returned HTTP 503", exception.getMessage());
    }

    @Test
    void shouldReturnResponseWhenAuthorizationSucceeds() {
        CardAuthorizationRequest request = CardAuthorizationRequest.builder()
                .transactionId("PG20260321200000ABCDEF")
                .cardNumber("4111111111111111")
                .amount(new BigDecimal("10000"))
                .merchantId("MERCHANT-001")
                .build();
        CardAuthorizationResponse response = CardAuthorizationResponse.builder()
                .transactionId("PG20260321200000ABCDEF")
                .approvalNumber("12345678")
                .responseCode("00")
                .message("Approved")
                .amount(new BigDecimal("10000"))
                .approved(true)
                .build();

        when(serviceClient.authorize(request)).thenReturn(response);

        CardAuthorizationResponse actual = client.authorize(request);

        assertEquals("00", actual.getResponseCode());
        assertEquals("12345678", actual.getApprovalNumber());
    }

    @Test
    void shouldRetryCommunicationFailureAndSucceedOnSecondAttempt() {
        CardAuthorizationRequest request = CardAuthorizationRequest.builder()
                .transactionId("PG20260321200000ABCDEF")
                .cardNumber("4111111111111111")
                .amount(new BigDecimal("10000"))
                .merchantId("MERCHANT-001")
                .build();
        CardAuthorizationResponse response = CardAuthorizationResponse.builder()
                .transactionId("PG20260321200000ABCDEF")
                .approvalNumber("12345678")
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
    void shouldNotRetryDownstreamFailure() {
        CardAuthorizationRequest request = CardAuthorizationRequest.builder()
                .transactionId("PG20260321200000ABCDEF")
                .cardNumber("4111111111111111")
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

        assertThrows(CardAuthorizationClientException.class, () -> client.authorize(request));

        verify(serviceClient, times(1)).authorize(request);
    }
}
