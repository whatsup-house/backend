package com.whatsuphouse.backend.domain.payment.dto.response;

import com.whatsuphouse.backend.domain.payment.entity.PayoutRequest;
import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import com.whatsuphouse.backend.domain.payment.enums.PayoutStatus;
import com.whatsuphouse.backend.domain.payment.enums.PayoutType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/** 운영자 송금 목록 1행. user는 이체 받는 회원 이름. 송금 30일이 지난 건은 accountNumber가 끝 4자리만 보인다. */
public record AdminPayoutResponse(
        UUID id,
        PayoutType type,
        @Schema(description = "회원 이름", example = "김철수") String user,
        int amount,
        String bank,
        String accountNumber,
        String holder,
        PayoutStatus status,
        LocalDateTime createdAt
) {
    public static AdminPayoutResponse from(PayoutRequest payout) {
        RefundAccount account = payout.getAccount();
        return new AdminPayoutResponse(payout.getId(), payout.getType(), payout.getUser().getName(), payout.getAmount(),
                account != null ? account.getBank() : null,
                account != null ? account.getAccountNumber() : null,
                account != null ? account.getHolder() : null,
                payout.getStatus(), payout.getCreatedAt());
    }
}
