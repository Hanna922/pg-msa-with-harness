package dev.pg.client;

import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import org.springframework.stereotype.Component;

@Component
public class CardAuthorizationClient {

    private final CardAuthorizationServiceClient serviceClient;

    public CardAuthorizationClient(CardAuthorizationServiceClient serviceClient) {
        this.serviceClient = serviceClient;
    }

    public CardAuthorizationResponse authorize(CardAuthorizationRequest request) {
        try {
            CardAuthorizationResponse response = serviceClient.authorize(request);

            if (response == null) {
                return CardAuthorizationResponse.builder()
                        .transactionId(request.getTransactionId())
                        .responseCode("96")
                        .message("Empty response from card authorization service")
                        .amount(request.getAmount())
                        .approved(false)
                        .build();
            }

            return response;
        } catch (Exception e) {
            return CardAuthorizationResponse.builder()
                    .transactionId(request.getTransactionId())
                    .responseCode("96")
                    .message("Card authorization service call failed")
                    .amount(request.getAmount())
                    .approved(false)
                    .build();
        }
    }
}
