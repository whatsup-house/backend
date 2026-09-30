package com.whatsuphouse.backend.domain.matching.dto.request;

import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 운영자 참석 상태 확정. ATTENDED·NO_SHOW·CANCELED_LATE만 받는다(SCHEDULED·CANCELED_EARLY는 시스템 전용, 400). (KAN-349) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStatusRequest {

    @NotNull
    @Schema(description = "바꿀 참석 상태(ATTENDED|NO_SHOW|CANCELED_LATE)", example = "NO_SHOW")
    private AttendanceStatus status;
}
