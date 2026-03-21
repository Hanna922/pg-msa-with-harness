package dev.merchant.controller;

import dev.merchant.dto.PaymentRequestDto;
import dev.merchant.dto.PaymentResponseDto;
import dev.merchant.entity.Payment;
import dev.merchant.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponseDto> pay(@RequestBody PaymentRequestDto requestDto) {
        Payment payment = paymentService.processPayment(requestDto);
        PaymentResponseDto response = PaymentResponseDto.from(payment);

        if (response.isApproved()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }
}
