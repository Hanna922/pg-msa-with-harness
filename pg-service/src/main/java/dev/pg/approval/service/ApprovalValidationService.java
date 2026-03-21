package dev.pg.approval.service;

import dev.pg.dto.MerchantApprovalRequest;
import org.springframework.stereotype.Service;

@Service
public class ApprovalValidationService {

    public void validate(MerchantApprovalRequest request) {
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
