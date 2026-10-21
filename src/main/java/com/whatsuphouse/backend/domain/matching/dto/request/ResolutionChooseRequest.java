package com.whatsuphouse.backend.domain.matching.dto.request;

import com.whatsuphouse.backend.domain.matching.enums.ResolutionChoice;
import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionChooseRequest {

    @NotNull
    @Schema(description = "선택. TRANSFER=대체 회차 이동, KEEP_TICKET=이용권 보관(복원), REFUND=환불", example = "TRANSFER")
    private ResolutionChoice choice;

    @Schema(description = "옮길 회차 ID. TRANSFER일 때 필수이며 제안 회차 중 하나여야 한다",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", nullable = true)
    private UUID sessionId;

    @Valid
    @Schema(description = "환불받을 계좌. REFUND일 때 필수(없으면 REFUND_ACCOUNT_REQUIRED)", nullable = true)
    private RefundAccount refundAccount;

    @Schema(description = "TRANSFER 후 희망 지역 라벨(GET /api/dining/regions 값), 지망 순. 생략·빈 목록이면 기존 희망 지역 유지. "
            + "모르는 라벨은 INVALID_REGION", example = "[\"잠실\", \"홍대/합정\"]", nullable = true)
    private List<@NotNull String> regions;

    public ResolutionChooseRequest(ResolutionChoice choice, UUID sessionId) {
        this(choice, sessionId, null, null);
    }

    public ResolutionChooseRequest(ResolutionChoice choice, UUID sessionId, RefundAccount refundAccount) {
        this(choice, sessionId, refundAccount, null);
    }
}
