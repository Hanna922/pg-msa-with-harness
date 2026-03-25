package dev.settlement.repository;

import dev.settlement.entity.SettlementTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, String> {

    Optional<SettlementTransaction> findByPgTransactionId(String pgTransactionId);
}
