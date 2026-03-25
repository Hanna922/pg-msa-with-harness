package dev.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.settlement.dto.RunSettlementRequest;
import dev.settlement.dto.SettlementResponse;
import dev.settlement.service.SettlementBatchService;
import dev.settlement.service.SettlementQueryService;
import dev.settlement.service.SettlementRunResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementController.class)
class SettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SettlementBatchService settlementBatchService;

    @MockitoBean
    private SettlementQueryService settlementQueryService;

    @Test
    void run_returnsProcessedSummary() throws Exception {
        when(settlementBatchService.run(LocalDate.of(2026, 3, 25)))
                .thenReturn(new SettlementRunResult(2, 1));

        mockMvc.perform(post("/api/settlements/run")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RunSettlementRequest(LocalDate.of(2026, 3, 25)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(2))
                .andExpect(jsonPath("$.createdCount").value(1));
    }

    @Test
    void getAll_returnsSettlementList() throws Exception {
        when(settlementQueryService.findAll()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].settlementId").value("STL-001"))
                .andExpect(jsonPath("$[0].pgTransactionId").value("PG20260324A1B2C3"))
                .andExpect(jsonPath("$[0].status").value("SETTLED"));
    }

    @Test
    void getById_returnsSettlementDetail() throws Exception {
        when(settlementQueryService.getBySettlementId("STL-001")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/settlements/STL-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value("STL-001"))
                .andExpect(jsonPath("$.feeAmount").value(300.00))
                .andExpect(jsonPath("$.netAmount").value(9700.00));
    }

    private SettlementResponse sampleResponse() {
        return SettlementResponse.builder()
                .settlementId("STL-001")
                .pgTransactionId("PG20260324A1B2C3")
                .merchantId("MERCHANT-001")
                .amount(new BigDecimal("10000.00"))
                .feeAmount(new BigDecimal("300.00"))
                .netAmount(new BigDecimal("9700.00"))
                .currency("KRW")
                .settlementDate(LocalDate.of(2026, 3, 25))
                .status("SETTLED")
                .build();
    }
}
