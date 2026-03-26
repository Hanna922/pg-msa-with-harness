package dev.ledger.controller;

import dev.ledger.dto.CreateLedgerTransactionRequest;
import dev.ledger.dto.LedgerTransactionResponse;
import dev.ledger.dto.UpdateSettlementStatusRequest;
import dev.ledger.service.LedgerTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/ledger/transactions")
@RequiredArgsConstructor
public class LedgerTransactionController {

    private final LedgerTransactionService service;

    @PostMapping
    public LedgerTransactionResponse create(@RequestBody CreateLedgerTransactionRequest request) {
        return service.createOrUpdate(request);
    }

    @GetMapping("/{pgTransactionId}")
    public LedgerTransactionResponse getByPgTransactionId(@PathVariable String pgTransactionId) {
        return service.getByPgTransactionId(pgTransactionId);
    }

    @GetMapping
    public List<LedgerTransactionResponse> findTransactions(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String settlementStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime approvedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime approvedTo
    ) {
        return service.findTransactions(merchantId, approvalStatus, settlementStatus, approvedFrom, approvedTo);
    }

    @PatchMapping("/{pgTransactionId}/settlement-status")
    public LedgerTransactionResponse updateSettlementStatus(
            @PathVariable String pgTransactionId,
            @RequestBody UpdateSettlementStatusRequest request
    ) {
        return service.updateSettlementStatus(pgTransactionId, request.settlementStatus());
    }
}
