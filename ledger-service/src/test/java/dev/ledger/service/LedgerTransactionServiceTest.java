package dev.ledger.service;

import dev.ledger.dto.CreateLedgerTransactionRequest;
import dev.ledger.dto.LedgerTransactionResponse;
import dev.ledger.entity.ApprovalStatus;
import dev.ledger.entity.LedgerTransactionRecord;
import dev.ledger.entity.SettlementStatus;
import dev.ledger.repository.LedgerTransactionRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerTransactionServiceTest {

    @Mock
    private LedgerTransactionRecordRepository repository;

    @InjectMocks
    private LedgerTransactionService service;

    @Test
    void createOrUpdate_storesApprovedTransaction() {
        CreateLedgerTransactionRequest request = CreateLedgerTransactionRequest.builder()
                .pgTransactionId("PG20260324A1B2C3")
                .merchantTransactionId("M-001")
                .merchantId("MERCHANT-001")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .approvalStatus(ApprovalStatus.APPROVED)
                .settlementStatus(SettlementStatus.NOT_READY)
                .acquirerType("CARD_AUTHORIZATION_SERVICE")
                .responseCode("0000")
                .message("Approved")
                .approvalNumber("AP-123456")
                .approvedAt(LocalDateTime.of(2026, 3, 24, 12, 0))
                .build();

        when(repository.findByPgTransactionId("PG20260324A1B2C3")).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(LedgerTransactionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LedgerTransactionResponse response = service.createOrUpdate(request);

        ArgumentCaptor<LedgerTransactionRecord> captor = ArgumentCaptor.forClass(LedgerTransactionRecord.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPgTransactionId()).isEqualTo("PG20260324A1B2C3");
        assertThat(response.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response.getSettlementStatus()).isEqualTo(SettlementStatus.NOT_READY);
    }
}
