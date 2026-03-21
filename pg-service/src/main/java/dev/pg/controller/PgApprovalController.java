package dev.pg.controller;

import dev.pg.approval.service.PgApprovalFacade;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pg")
public class PgApprovalController {

    private final PgApprovalFacade pgApprovalFacade;

    public PgApprovalController(PgApprovalFacade pgApprovalFacade) {
        this.pgApprovalFacade = pgApprovalFacade;
    }

    @PostMapping("/approve")
    public MerchantApprovalResponse approve(@RequestBody MerchantApprovalRequest request) {
        return pgApprovalFacade.approve(request);
    }
}
