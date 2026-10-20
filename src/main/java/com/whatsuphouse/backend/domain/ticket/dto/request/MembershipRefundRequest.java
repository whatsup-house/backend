package com.whatsuphouse.backend.domain.ticket.dto.request;

import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MembershipRefundRequest {

    // 누락은 서비스가 REFUND_ACCOUNT_REQUIRED로 돌려준다(FE가 코드로 분기).
    @Valid
    @Schema(description = "환불받을 계좌(은행·계좌번호·예금주)")
    private RefundAccount refundAccount;
}
