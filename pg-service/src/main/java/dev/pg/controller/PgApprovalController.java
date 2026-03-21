package dev.pg.controller;

import dev.pg.approval.service.PgApprovalFacade;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/pg")
public class PgApprovalController {

    private final PgApprovalFacade pgApprovalFacade;

    public PgApprovalController(PgApprovalFacade pgApprovalFacade) {
        this.pgApprovalFacade = pgApprovalFacade;
    }

    @PostMapping("/approve")
    public ResponseEntity<MerchantApprovalResponse> approve(@RequestBody MerchantApprovalRequest request) {
        try {
            return ResponseEntity.ok(pgApprovalFacade.approve(request));
        } catch (IllegalArgumentException e) {
            MerchantApprovalResponse response = MerchantApprovalResponse.builder()
                    .merchantTransactionId(request != null ? request.getMerchantTransactionId() : null)
                    .approved(false)
                    .responseCode("96")
                    .message(e.getMessage())
                    .approvedAt(LocalDateTime.now())
                    .build();
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            MerchantApprovalResponse response = MerchantApprovalResponse.builder()
                    .merchantTransactionId(request != null ? request.getMerchantTransactionId() : null)
                    .approved(false)
                    .responseCode("96")
                    .message("PG approval processing failed")
                    .approvedAt(LocalDateTime.now())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
