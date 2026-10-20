package com.whatsuphouse.backend.domain.application.client.dto.request;

import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 우연한 식탁 취소 요청 본문(선택). (KAN-418) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiningCancelRequest {

    // 이용권 환불이 있는데 누락하면 서비스가 REFUND_ACCOUNT_REQUIRED로 돌려준다(FE가 코드로 분기).
    @Valid
    @Schema(description = "이용권 환불받을 계좌(은행·계좌번호·예금주). 미리보기 requiresRefundAccount=true일 때 필수")
    private RefundAccount refundAccount;
}
