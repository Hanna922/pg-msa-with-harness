package dev.pg.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pg.approval.service.PgApprovalFacade;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import dev.pg.support.exception.BusinessException;
import dev.pg.support.exception.ErrorCode;
import dev.pg.support.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PgApprovalController.class)
@Import(GlobalExceptionHandler.class)
class PgApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PgApprovalFacade pgApprovalFacade;

    @Test
    void shouldReturnOkWhenApprovalSucceeds() throws Exception {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603210010")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();

        when(pgApprovalFacade.approve(any())).thenReturn(
                MerchantApprovalResponse.builder()
                        .merchantTransactionId("M202603210010")
                        .pgTransactionId("PG20260321195500ABCDEF")
                        .approved(true)
                        .responseCode("00")
                        .message("Approved")
                        .approvalNumber("12345678")
                        .approvedAt(LocalDateTime.of(2026, 3, 21, 19, 55, 5))
                        .build()
        );

        mockMvc.perform(post("/api/pg/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantTransactionId").value("M202603210010"))
                .andExpect(jsonPath("$.pgTransactionId").value("PG20260321195500ABCDEF"))
                .andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    void shouldReturnBadRequestWhenFacadeRejectsRequest() throws Exception {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603210011")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();

        when(pgApprovalFacade.approve(any())).thenThrow(new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "merchantTransactionId is required"
        ));

        mockMvc.perform(post("/api/pg/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.responseCode").value("96"))
                .andExpect(jsonPath("$.message").value("merchantTransactionId is required"));
    }

    @Test
    void shouldReturnInternalServerErrorWhenFacadeThrowsUnexpectedException() throws Exception {
        MerchantApprovalRequest request = MerchantApprovalRequest.builder()
                .merchantTransactionId("M202603210012")
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();

        when(pgApprovalFacade.approve(any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/pg/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.responseCode").value("96"))
                .andExpect(jsonPath("$.message").value("PG approval processing failed"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenCommunicationFailureOccurs() throws Exception {
        MerchantApprovalRequest request = createRequest("M202603210013");

        when(pgApprovalFacade.approve(any())).thenThrow(new BusinessException(
                ErrorCode.CARD_AUTH_COMMUNICATION_FAILURE,
                "Card authorization service communication failed"
        ));

        mockMvc.perform(post("/api/pg/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.responseCode").value("96"))
                .andExpect(jsonPath("$.message").value("Card authorization service communication failed"));
    }

    @Test
    void shouldReturnBadGatewayWhenDownstreamFailureOccurs() throws Exception {
        MerchantApprovalRequest request = createRequest("M202603210014");

        when(pgApprovalFacade.approve(any())).thenThrow(new BusinessException(
                ErrorCode.CARD_AUTH_DOWNSTREAM_FAILURE,
                "Card authorization service returned HTTP 503"
        ));

        mockMvc.perform(post("/api/pg/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.responseCode").value("96"))
                .andExpect(jsonPath("$.message").value("Card authorization service returned HTTP 503"));
    }

    @Test
    void shouldReturnTooManyRequestsWhenCircuitBreakerIsOpen() throws Exception {
        MerchantApprovalRequest request = createRequest("M202603210015");

        when(pgApprovalFacade.approve(any())).thenThrow(new BusinessException(
                ErrorCode.CARD_AUTH_CIRCUIT_OPEN,
                "Card authorization circuit breaker is open"
        ));

        mockMvc.perform(post("/api/pg/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.responseCode").value("96"))
                .andExpect(jsonPath("$.message").value("Card authorization circuit breaker is open"));
    }

    private MerchantApprovalRequest createRequest(String merchantTransactionId) {
        return MerchantApprovalRequest.builder()
                .merchantTransactionId(merchantTransactionId)
                .merchantId("MERCHANT-001")
                .cardNumber("4111111111111111")
                .expiryDate("2712")
                .amount(new BigDecimal("10000"))
                .currency("KRW")
                .build();
    }
}
