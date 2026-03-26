package dev.pg.client;

import dev.pg.client.dto.CreateLedgerTransactionRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ledger-service")
public interface LedgerServiceClient {

    @PostMapping("/api/ledger/transactions")
    void createTransaction(@RequestBody CreateLedgerTransactionRequest request);
}
