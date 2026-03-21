package dev.pg.ledger.service;

import dev.pg.ledger.entity.PaymentTransaction;
import dev.pg.ledger.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IdempotencyService {

    private final PaymentTransactionRepository paymentTransactionRepository;

    public IdempotencyService(PaymentTransactionRepository paymentTransactionRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    public Optional<PaymentTransaction> findExistingTransaction(String merchantTransactionId) {
        return paymentTransactionRepository.findByMerchantTransactionId(merchantTransactionId);
    }
}
