package dev.pg.client;

import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "card-authorization-service")
public interface CardAuthorizationServiceClient {

    @PostMapping("/api/authorization/request")
    CardAuthorizationResponse authorize(@RequestBody CardAuthorizationRequest request);
}
