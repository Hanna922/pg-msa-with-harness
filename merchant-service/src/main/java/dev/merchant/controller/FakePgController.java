package dev.merchant.controller;

import dev.merchant.client.PgAuthRequest;
import dev.merchant.client.PgAuthResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
public class FakePgController {

    @PostMapping("/api/pg/approve")
    public PgAuthResponse mockPgApprove(@RequestBody PgAuthRequest request) {
        PgAuthResponse response = new PgAuthResponse();
        response.setMerchantTransactionId(request.getMerchantTransactionId());
        response.setPgTransactionId("PG" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        response.setApproved(true);
        response.setResponseCode("00");
        response.setMessage("Approved");
        response.setApprovalNumber("12345678");
        response.setApprovedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        return response;
    }
}
