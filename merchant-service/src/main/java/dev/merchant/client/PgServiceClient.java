package dev.merchant.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service")
public interface PgServiceClient {
    @PostMapping("/api/payments/approve")
    PgAuthResponse requestPaymentAuth(@RequestBody PgAuthRequest request);
}
