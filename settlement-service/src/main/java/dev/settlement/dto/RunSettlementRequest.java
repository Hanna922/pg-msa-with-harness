package dev.settlement.dto;

import java.time.LocalDate;

public record RunSettlementRequest(LocalDate settlementDate) {
}
