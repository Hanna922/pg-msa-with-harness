package com.card.payment.authorization.client;

import com.card.payment.authorization.dto.BalanceRequest;
import com.card.payment.authorization.dto.BalanceResponse;
import com.card.payment.authorization.dto.DebitRequest;
import com.card.payment.authorization.dto.DebitResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class BankClientImpl implements BankClient {

    private static final Logger logger = LoggerFactory.getLogger(BankClientImpl.class);
    private static final int MAX_RETRY_COUNT = 1;
    private static final Duration BALANCE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEBIT_TIMEOUT = Duration.ofSeconds(30);

    private final BankServiceClient bankServiceClient;
    private final RestClient balanceRestClient;
    private final RestClient debitRestClient;

    @Autowired
    public BankClientImpl(BankServiceClient bankServiceClient) {
        this.bankServiceClient = bankServiceClient;
        this.balanceRestClient = null;
        this.debitRestClient = null;
    }

    // Test-only constructor for MockWebServer-based tests.
    public BankClientImpl(String bankServiceUrl) {
        this.bankServiceClient = null;

        JdkClientHttpRequestFactory balanceRequestFactory = new JdkClientHttpRequestFactory();
        balanceRequestFactory.setReadTimeout(BALANCE_TIMEOUT);

        this.balanceRestClient = RestClient.builder()
                .baseUrl(bankServiceUrl)
                .requestFactory(balanceRequestFactory)
                .build();

        JdkClientHttpRequestFactory debitRequestFactory = new JdkClientHttpRequestFactory();
        debitRequestFactory.setReadTimeout(DEBIT_TIMEOUT);

        this.debitRestClient = RestClient.builder()
                .baseUrl(bankServiceUrl)
                .requestFactory(debitRequestFactory)
                .build();
    }

    @Override
    public BalanceResponse checkBalance(String cardNumber, BigDecimal amount) {
        BalanceRequest request = new BalanceRequest(cardNumber, amount);
        return executeWithRetry(() -> performBalanceCheck(request), "Balance check");
    }

    @Override
    public DebitResponse requestDebit(String cardNumber, BigDecimal amount, String transactionId) {
        DebitRequest request = new DebitRequest(cardNumber, amount, transactionId);
        return executeWithRetry(() -> performDebit(request), "Debit request");
    }

    private BalanceResponse performBalanceCheck(BalanceRequest request) {
        logger.info("Bank balance request: cardNumber={}, amount={}",
                maskCardNumber(request.getCardNumber()), request.getAmount());

        try {
            BalanceResponse response;
            if (bankServiceClient != null) {
                response = bankServiceClient.checkBalance(request);
            } else {
                response = balanceRestClient.post()
                        .uri("/api/account/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(BalanceResponse.class);
            }

            logger.info("Bank balance request succeeded: sufficient={}", response != null && response.isSufficient());
            return response;
        } catch (Exception e) {
            logger.error("Bank balance request failed: {}", e.getMessage());
            throw new BankClientException("Balance check failed", e);
        }
    }

    private DebitResponse performDebit(DebitRequest request) {
        logger.info("Bank debit request: cardNumber={}, amount={}, transactionId={}",
                maskCardNumber(request.getCardNumber()), request.getAmount(), request.getTransactionId());

        try {
            DebitResponse response;
            if (bankServiceClient != null) {
                response = bankServiceClient.requestDebit(request);
            } else {
                response = debitRestClient.post()
                        .uri("/api/account/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(DebitResponse.class);
            }

            logger.info("Bank debit request succeeded: transactionId={}",
                    response != null ? response.getTransactionId() : null);
            return response;
        } catch (Exception e) {
            logger.error("Bank debit request failed: {}", e.getMessage());
            throw new BankClientException("Debit failed", e);
        }
    }

    private <T> T executeWithRetry(RetryableOperation<T> operation, String operationName) {
        int attemptCount = 0;
        Exception lastException = null;

        while (attemptCount <= MAX_RETRY_COUNT) {
            try {
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                attemptCount++;

                if (attemptCount <= MAX_RETRY_COUNT) {
                    logger.warn("{} failed (attempt {}/{}), retrying...",
                            operationName, attemptCount, MAX_RETRY_COUNT + 1);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BankClientException(operationName + " retry interrupted", ie);
                    }
                } else {
                    logger.error("{} failed after {} attempts", operationName, attemptCount);
                }
            }
        }

        throw new BankClientException(operationName + " failed", lastException);
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return "****";
        }
        return cardNumber.substring(0, 4) + "****" + cardNumber.substring(cardNumber.length() - 4);
    }

    @FunctionalInterface
    private interface RetryableOperation<T> {
        T execute() throws Exception;
    }

    public static class BankClientException extends RuntimeException {
        public BankClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
