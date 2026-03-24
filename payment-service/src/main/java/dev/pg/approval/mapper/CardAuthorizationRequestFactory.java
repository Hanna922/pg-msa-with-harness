package dev.pg.approval.mapper;

import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.MerchantApprovalRequest;
import org.springframework.stereotype.Component;

@Component
public class CardAuthorizationRequestFactory {

    public CardAuthorizationRequest create(MerchantApprovalRequest request, String pgTransactionId) {
        return CardAuthorizationRequest.builder()
                .transactionId(pgTransactionId)
                .cardNumber(request.getCardNumber())
                .amount(request.getAmount())
                .merchantId(request.getMerchantId())
                .terminalId(null)
                .pin(null)
                .build();
    }
}
