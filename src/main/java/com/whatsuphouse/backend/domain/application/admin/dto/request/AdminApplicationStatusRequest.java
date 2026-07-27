package com.whatsuphouse.backend.domain.application.admin.dto.request;

import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminApplicationStatusRequest {

    @Schema(example = "CONFIRMED", description = "변경할 신청 상태 (CONFIRMED, CANCELLED, ATTENDED)")
    private ApplicationStatus status;
}
