package dev.pg.ledger.repository;

import dev.pg.ledger.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {

    Optional<PaymentTransaction> findByMerchantTransactionId(String merchantTransactionId);
}
