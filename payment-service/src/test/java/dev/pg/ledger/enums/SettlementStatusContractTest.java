package dev.pg.ledger.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementStatusContractTest {

    @Test
    void shouldMatchLedgerServiceSettlementStatusContract() {
        List<String> values = Arrays.stream(SettlementStatus.values())
                .map(Enum::name)
                .toList();

        assertThat(values).containsExactly("NOT_READY", "READY", "SETTLED");
    }
}
