package com.whatsuphouse.backend.domain.mileage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminMileageRequest {

    @Schema(description = "조정 금액 (양수=부여, 음수=차감)", example = "1000")
    @NotNull
    private Integer amount;

    @Schema(description = "조정 사유", example = "이벤트 보상")
    private String adjustReason;
}
