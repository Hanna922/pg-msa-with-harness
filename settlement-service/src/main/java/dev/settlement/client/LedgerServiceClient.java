package dev.settlement.client;

import dev.settlement.client.dto.LedgerTransactionResponse;
import dev.settlement.client.dto.UpdateSettlementStatusRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@FeignClient(name = "ledger-service")
public interface LedgerServiceClient {

    @GetMapping("/api/ledger/transactions")
    List<LedgerTransactionResponse> findTransactions(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String settlementStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime approvedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime approvedTo
    );

    @PatchMapping("/api/ledger/transactions/{pgTransactionId}/settlement-status")
    void updateSettlementStatus(
            @PathVariable String pgTransactionId,
            @RequestBody UpdateSettlementStatusRequest request
    );
}
