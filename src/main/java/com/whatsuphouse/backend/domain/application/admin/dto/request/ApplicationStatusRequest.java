package com.whatsuphouse.backend.domain.application.admin.dto.request;

import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusRequest {

    @NotNull(message = "상태는 필수입니다.")
    @Schema(example = "CONFIRMED", description = "변경할 신청 상태 (CONFIRMED, REJECTED, ATTENDED)")
    private ApplicationStatus status;

    @Schema(example = "운영 기준에 맞지 않음", description = "REJECTED 변경 시 필수인 거절 사유")
    private String rejectionReason;
}
