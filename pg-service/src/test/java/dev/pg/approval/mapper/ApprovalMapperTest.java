package dev.pg.approval.mapper;

import dev.pg.dto.MerchantApprovalResponse;
import dev.pg.ledger.entity.PaymentTransaction;
import dev.pg.ledger.enums.ApprovalStatus;
import dev.pg.ledger.enums.SettlementStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalMapperTest {

    private final ApprovalMapper approvalMapper = new ApprovalMapper();

    @Test
    void shouldMapApprovedTransactionToMerchantApprovalResponse() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .pgTransactionId("PG20260321193000ABCDEF")
                .merchantTransactionId("M202603210001")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .approvalStatus(ApprovalStatus.APPROVED)
                .settlementStatus(SettlementStatus.READY)
                .responseCode("00")
                .message("Approved")
                .approvalNumber("12345678")
                .requestedAt(LocalDateTime.of(2026, 3, 21, 19, 30, 0))
                .respondedAt(LocalDateTime.of(2026, 3, 21, 19, 30, 5))
                .approvedAt(LocalDateTime.of(2026, 3, 21, 19, 30, 5))
                .build();

        MerchantApprovalResponse response = approvalMapper.toMerchantApprovalResponse(transaction);

        assertEquals("M202603210001", response.getMerchantTransactionId());
        assertEquals("PG20260321193000ABCDEF", response.getPgTransactionId());
        assertTrue(response.isApproved());
        assertEquals("00", response.getResponseCode());
        assertEquals("12345678", response.getApprovalNumber());
        assertEquals(LocalDateTime.of(2026, 3, 21, 19, 30, 5), response.getApprovedAt());
    }

    @Test
    void shouldUseRespondedAtForFailedTransaction() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .pgTransactionId("PG20260321193000ZZZZZZ")
                .merchantTransactionId("M202603210002")
                .merchantId("MERCHANT-001")
                .maskedCardNumber("411111******1111")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .approvalStatus(ApprovalStatus.FAILED)
                .settlementStatus(SettlementStatus.NOT_READY)
                .responseCode("51")
                .message("Insufficient funds")
                .requestedAt(LocalDateTime.of(2026, 3, 21, 19, 31, 0))
                .respondedAt(LocalDateTime.of(2026, 3, 21, 19, 31, 2))
                .build();

        MerchantApprovalResponse response = approvalMapper.toMerchantApprovalResponse(transaction);

        assertFalse(response.isApproved());
        assertEquals("51", response.getResponseCode());
        assertEquals(LocalDateTime.of(2026, 3, 21, 19, 31, 2), response.getApprovedAt());
    }
}
