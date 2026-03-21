package dev.merchant.dto;

import lombok.Getter;

@Getter
public class PaymentRequestDto {
    private String merchantId;
    private String cardNumber;
    private String expiryDate;
    private Integer amount;
}