package com.whatsuphouse.backend.domain.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** 심사용 입금 시뮬레이터. passId(이용권 구매, 같은 신청 보증금 합산) 또는 applicationId(보증금만) 중 정확히 하나. */
@Getter
@NoArgsConstructor
public class DemoDepositRequest {

    @Schema(description = "결제 대기 이용권 ID", example = "b3f1c2d4-...")
    private UUID passId;

    @Schema(description = "보증금만 입금할 신청 ID", example = "a1b2c3d4-...")
    private UUID applicationId;
}
