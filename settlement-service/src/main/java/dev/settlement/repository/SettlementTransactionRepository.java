package dev.settlement.repository;

import dev.settlement.entity.SettlementTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, String> {
}
