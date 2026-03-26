package dev.settlement.client;

import dev.settlement.client.dto.LedgerTransactionResponse;
import dev.settlement.client.dto.SettlementStatus;
import dev.settlement.client.dto.UpdateSettlementStatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LedgerClient {

    private final LedgerServiceClient ledgerServiceClient;

    public List<LedgerTransactionResponse> findTransactionsForSettlement(
            String merchantId,
            String approvalStatus,
            String settlementStatus,
            LocalDateTime approvedFrom,
            LocalDateTime approvedTo
    ) {
        return ledgerServiceClient.findTransactions(
                merchantId,
                approvalStatus,
                settlementStatus,
                approvedFrom,
                approvedTo
        );
    }

    public void markTransactionAsSettled(String pgTransactionId) {
        ledgerServiceClient.updateSettlementStatus(
                pgTransactionId,
                new UpdateSettlementStatusRequest(SettlementStatus.SETTLED)
        );
    }
}
