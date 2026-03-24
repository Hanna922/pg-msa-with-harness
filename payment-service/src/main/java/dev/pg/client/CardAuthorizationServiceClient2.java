package dev.pg.client;

import dev.pg.config.CardAuthorizationFeignConfig2;
import dev.pg.dto.CardAuthorizationRequest;
import dev.pg.dto.CardAuthorizationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "card-authorization-service-2", configuration = CardAuthorizationFeignConfig2.class)
public interface CardAuthorizationServiceClient2 {

    @PostMapping("/api/authorization/request")
    CardAuthorizationResponse authorize(@RequestBody CardAuthorizationRequest request);
}
