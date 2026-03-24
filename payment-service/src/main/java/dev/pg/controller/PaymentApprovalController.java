package dev.pg.controller;

import dev.pg.approval.service.PaymentApprovalFacade;
import dev.pg.dto.MerchantApprovalRequest;
import dev.pg.dto.MerchantApprovalResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentApprovalController {

    private final PaymentApprovalFacade paymentApprovalFacade;

    public PaymentApprovalController(PaymentApprovalFacade paymentApprovalFacade) {
        this.paymentApprovalFacade = paymentApprovalFacade;
    }

    @PostMapping("/approve")
    public MerchantApprovalResponse approve(@RequestBody MerchantApprovalRequest request) {
        return paymentApprovalFacade.approve(request);
    }
}
