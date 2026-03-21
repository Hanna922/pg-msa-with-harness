package dev.pg.approval.service;

import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.support.exception.BusinessException;
import dev.pg.support.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class ApprovalValidationService {

    public void validate(MerchantApprovalRequest request) {
        if (request == null) {
            throw invalidRequest("Request body is required");
        }
        if (isBlank(request.getMerchantTransactionId())) {
            throw invalidRequest("merchantTransactionId is required");
        }
        if (isBlank(request.getMerchantId())) {
            throw invalidRequest("merchantId is required");
        }
        if (isBlank(request.getCardNumber())) {
            throw invalidRequest("cardNumber is required");
        }
        if (isBlank(request.getExpiryDate())) {
            throw invalidRequest("expiryDate is required");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw invalidRequest("amount must be greater than zero");
        }
        if (isBlank(request.getCurrency())) {
            throw invalidRequest("currency is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private BusinessException invalidRequest(String message) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, message);
    }
}
