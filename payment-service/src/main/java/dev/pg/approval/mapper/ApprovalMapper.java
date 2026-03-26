package dev.pg.approval.mapper;

import dev.pg.dto.MerchantApprovalResponse;
import dev.pg.ledger.entity.PaymentTransaction;
import dev.pg.ledger.enums.ApprovalStatus;
import org.springframework.stereotype.Component;

@Component
public class ApprovalMapper {

    public MerchantApprovalResponse toMerchantApprovalResponse(PaymentTransaction transaction) {
        return MerchantApprovalResponse.builder()
                .merchantTransactionId(transaction.getMerchantTransactionId())
                .pgTransactionId(transaction.getPgTransactionId())
                .approved(transaction.getApprovalStatus() == ApprovalStatus.APPROVED)
                .responseCode(transaction.getResponseCode())
                .message(transaction.getMessage())
                .approvalNumber(transaction.getApprovalNumber())
                .approvedAt(transaction.getApprovedAt() != null ? transaction.getApprovedAt() : transaction.getRespondedAt())
                .build();
    }
}
