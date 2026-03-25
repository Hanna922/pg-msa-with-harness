package dev.settlement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class SettlementCalculationService {

    private final BigDecimal feeRate;

    public SettlementCalculationService(@Value("${settlement.fee-rate:0.03}") BigDecimal feeRate) {
        this.feeRate = feeRate;
    }

    public BigDecimal calculateFeeAmount(BigDecimal amount) {
        return amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateNetAmount(BigDecimal amount) {
        return amount.subtract(calculateFeeAmount(amount)).setScale(2, RoundingMode.HALF_UP);
    }
}
