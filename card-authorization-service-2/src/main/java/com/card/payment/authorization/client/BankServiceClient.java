package com.card.payment.authorization.client;

import com.card.payment.authorization.dto.BalanceRequest;
import com.card.payment.authorization.dto.BalanceResponse;
import com.card.payment.authorization.dto.DebitRequest;
import com.card.payment.authorization.dto.DebitResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "bank-service")
public interface BankServiceClient {

    @PostMapping("/api/account/balance")
    BalanceResponse checkBalance(@RequestBody BalanceRequest request);

    @PostMapping("/api/account/debit")
    DebitResponse requestDebit(@RequestBody DebitRequest request);
}
