package dev.pg.client;

import dev.pg.client.dto.CreateLedgerTransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerClient {

    private final LedgerServiceClient ledgerServiceClient;

    public void syncTransaction(CreateLedgerTransactionRequest request) {
        ledgerServiceClient.createTransaction(request);
        log.info("Synced transaction to ledger-service. pgTransactionId={}", request.getPgTransactionId());
    }
}
