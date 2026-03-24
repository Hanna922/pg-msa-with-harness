package dev.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledger.dto.CreateLedgerTransactionRequest;
import dev.ledger.dto.LedgerTransactionResponse;
import dev.ledger.dto.UpdateSettlementStatusRequest;
import dev.ledger.entity.ApprovalStatus;
import dev.ledger.entity.SettlementStatus;
import dev.ledger.service.LedgerTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LedgerTransactionController.class)
class LedgerTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LedgerTransactionService service;

    @Test
    void create_returnsStoredTransaction() throws Exception {
        CreateLedgerTransactionRequest request = CreateLedgerTransactionRequest.builder()
                .pgTransactionId("PG20260324A1B2C3")
                .merchantTransactionId("M-001")
                .merchantId("MERCHANT-001")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .approvalStatus(ApprovalStatus.APPROVED)
                .settlementStatus(SettlementStatus.NOT_READY)
                .build();

        when(service.createOrUpdate(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/ledger/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pgTransactionId").value("PG20260324A1B2C3"))
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    }

    @Test
    void getByPgTransactionId_returnsTransaction() throws Exception {
        when(service.getByPgTransactionId("PG20260324A1B2C3")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/ledger/transactions/PG20260324A1B2C3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pgTransactionId").value("PG20260324A1B2C3"));
    }

    @Test
    void updateSettlementStatus_returnsUpdatedTransaction() throws Exception {
        UpdateSettlementStatusRequest request = new UpdateSettlementStatusRequest(SettlementStatus.SETTLED);
        LedgerTransactionResponse updated = sampleResponse();
        updated.setSettlementStatus(SettlementStatus.SETTLED);

        when(service.updateSettlementStatus("PG20260324A1B2C3", SettlementStatus.SETTLED)).thenReturn(updated);

        mockMvc.perform(patch("/api/ledger/transactions/PG20260324A1B2C3/settlement-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementStatus").value("SETTLED"));
    }

    private LedgerTransactionResponse sampleResponse() {
        return LedgerTransactionResponse.builder()
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
                .recordedAt(LocalDateTime.of(2026, 3, 24, 12, 0, 1))
                .build();
    }
}
