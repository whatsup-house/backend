package com.whatsuphouse.backend.domain.dining.admin.dto.request;

import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.SafetyAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionCaseStatusRequest {

    @NotNull
    @Schema(description = "바꿀 상태. RESOLVED=처리 완료, OPEN=다시 열기", example = "RESOLVED")
    private ExceptionCaseStatus status;

    // 빈 값 검사는 RESOLVED일 때만 필요해 엔티티에서 한다(없으면 EXCEPTION_NOTE_REQUIRED 400).
    @Size(max = 2000, message = "처리 메모는 2000자 이하로 입력해주세요.")
    @Schema(description = "처리 메모. RESOLVED로 바꿀 때 필수", example = "식당 B로 수동 배정 완료")
    private String note;

    @Schema(description = "SAFETY 예외 처리 조치(선택). WARN=기록만, RESTRICT/BAN=우연한 식탁 참여 제한", example = "WARN",
            nullable = true)
    private SafetyAction action;
}
