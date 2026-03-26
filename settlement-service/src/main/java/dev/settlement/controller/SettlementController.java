package dev.settlement.controller;

import dev.settlement.dto.RunSettlementRequest;
import dev.settlement.dto.SettlementResponse;
import dev.settlement.service.SettlementBatchService;
import dev.settlement.service.SettlementQueryService;
import dev.settlement.service.SettlementRunResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementBatchService settlementBatchService;
    private final SettlementQueryService settlementQueryService;

    public SettlementController(
            SettlementBatchService settlementBatchService,
            SettlementQueryService settlementQueryService
    ) {
        this.settlementBatchService = settlementBatchService;
        this.settlementQueryService = settlementQueryService;
    }

    @PostMapping("/run")
    public SettlementRunResult run(@RequestBody RunSettlementRequest request) {
        LocalDate settlementDate = request.settlementDate() != null ? request.settlementDate() : LocalDate.now();
        return settlementBatchService.run(settlementDate);
    }

    @GetMapping
    public List<SettlementResponse> findAll() {
        return settlementQueryService.findAll();
    }

    @GetMapping("/{settlementId}")
    public SettlementResponse getBySettlementId(@PathVariable String settlementId) {
        return settlementQueryService.getBySettlementId(settlementId);
    }
}
