package dev.ledger.repository;

import dev.ledger.entity.LedgerTransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface LedgerTransactionRecordRepository extends JpaRepository<LedgerTransactionRecord, Long>,
        JpaSpecificationExecutor<LedgerTransactionRecord> {

    Optional<LedgerTransactionRecord> findByPgTransactionId(String pgTransactionId);
}
