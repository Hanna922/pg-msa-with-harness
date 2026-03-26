package dev.ledger.dto;

import dev.ledger.entity.SettlementStatus;

public record UpdateSettlementStatusRequest(SettlementStatus settlementStatus) {
}
