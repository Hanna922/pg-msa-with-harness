package dev.merchant.service;

import dev.merchant.client.PgAuthRequest;
import dev.merchant.client.PgAuthResponse;
import dev.merchant.client.PgServiceClient;
import dev.merchant.dto.PaymentRequestDto;
import dev.merchant.entity.Payment;
import dev.merchant.entity.PaymentStatus;
import dev.merchant.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PgServiceClient pgServiceClient;

    @Transactional
    public Payment processPayment(PaymentRequestDto requestDto) {
        String merchantTxId = "M" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);

        Payment payment = Payment.builder()
                .merchantTransactionId(merchantTxId)
                .merchantId(requestDto.getMerchantId())
                .cardNumber(requestDto.getCardNumber())
                .amount(requestDto.getAmount())
                .status(PaymentStatus.READY)
                .build();
        paymentRepository.save(payment);

        String transmissionDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String stan = String.format("%06d", new Random().nextInt(999999));

        PgAuthRequest pgRequest = PgAuthRequest.builder()
                .merchantTransactionId(merchantTxId)
                .merchantId(requestDto.getMerchantId())
                .cardNumber(requestDto.getCardNumber())
                .expiryDate(requestDto.getExpiryDate())
                .amount(requestDto.getAmount())
                .currency("KRW")
                .mti("0100")
                .processingCode("000000")
                .transmissionDateTime(transmissionDateTime)
                .stan(stan)
                .build();

        try {
            log.info("PG authorization request: {}", merchantTxId);
            PgAuthResponse pgResponse = pgServiceClient.requestPaymentAuth(pgRequest);

            if (pgResponse.isApproved()) {
                payment.updateStatus(
                        PaymentStatus.APPROVED,
                        pgResponse.getPgTransactionId(),
                        pgResponse.getResponseCode(),
                        pgResponse.getMessage(),
                        LocalDateTime.now()
                );
            } else {
                payment.updateStatus(
                        PaymentStatus.FAILED,
                        pgResponse.getPgTransactionId(),
                        pgResponse.getResponseCode(),
                        pgResponse.getMessage(),
                        null
                );
            }
        } catch (Exception e) {
            log.error("PG communication failed - transactionId={}", merchantTxId, e);
            payment.updateStatus(PaymentStatus.FAILED, null, "99", "PG communication error", null);
        }

        return payment;
    }
}
