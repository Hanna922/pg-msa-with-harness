package dev.pg.service;

import dev.pg.client.CardAuthorizationClient;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class PgApprovalService {

    private static final DateTimeFormatter PG_TX_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CardAuthorizationClient cardAuthorizationClient;

    public PgApprovalService(CardAuthorizationClient cardAuthorizationClient) {
        this.cardAuthorizationClient = cardAuthorizationClient;
    }

    public MerchantApprovalResponse approve(MerchantApprovalRequest request) {
        validateRequest(request);

        String pgTransactionId = generatePgTransactionId();
        CardAuthorizationRequest cardRequest = CardAuthorizationRequest.builder()
                .transactionId(pgTransactionId)
                .cardNumber(request.getCardNumber())
                .amount(request.getAmount())
                .merchantId(request.getMerchantId())
                .terminalId(null)
                .pin(null)
                .build();

        CardAuthorizationResponse cardResponse = cardAuthorizationClient.authorize(cardRequest);
        return mapToMerchantResponse(request.getMerchantTransactionId(), pgTransactionId, cardResponse);
    }

    MerchantApprovalResponse mapToMerchantResponse(
            String merchantTransactionId,
            String pgTransactionId,
            CardAuthorizationResponse cardResponse
    ) {
        LocalDateTime approvedAt = cardResponse.getAuthorizationDate() != null
                ? cardResponse.getAuthorizationDate()
                : LocalDateTime.now();

        return MerchantApprovalResponse.builder()
                .merchantTransactionId(merchantTransactionId)
                .pgTransactionId(pgTransactionId)
                .approved(cardResponse.isApproved())
                .responseCode(cardResponse.getResponseCode())
                .message(cardResponse.getMessage())
                .approvalNumber(cardResponse.getApprovalNumber())
                .approvedAt(approvedAt)
                .build();
    }

    String generatePgTransactionId() {
        String timestamp = LocalDateTime.now().format(PG_TX_TIMESTAMP);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "PG" + timestamp + suffix;
    }

    private void validateRequest(MerchantApprovalRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (isBlank(request.getMerchantTransactionId())) {
            throw new IllegalArgumentException("merchantTransactionId is required");
        }
        if (isBlank(request.getMerchantId())) {
            throw new IllegalArgumentException("merchantId is required");
        }
        if (isBlank(request.getCardNumber())) {
            throw new IllegalArgumentException("cardNumber is required");
        }
        if (isBlank(request.getExpiryDate())) {
            throw new IllegalArgumentException("expiryDate is required");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (isBlank(request.getCurrency())) {
            throw new IllegalArgumentException("currency is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
