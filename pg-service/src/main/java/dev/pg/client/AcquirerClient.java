package dev.pg.client;

import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import dev.pg.routing.model.AcquirerType;

public interface AcquirerClient {

    AcquirerType getAcquirerType();

    CardAuthorizationResponse authorize(CardAuthorizationRequest request);
}
