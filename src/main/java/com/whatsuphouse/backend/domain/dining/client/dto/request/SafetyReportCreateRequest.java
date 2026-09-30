package com.whatsuphouse.backend.domain.dining.client.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SafetyReportCreateRequest {

    @NotNull
    @Schema(description = "신고할 같은 테이블 멤버의 회원 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID reportedUserId;

    @NotBlank
    @Size(max = 2000)
    @Schema(description = "신고 사유", example = "모임 중 불쾌한 언행이 있었어요")
    private String reason;
}
