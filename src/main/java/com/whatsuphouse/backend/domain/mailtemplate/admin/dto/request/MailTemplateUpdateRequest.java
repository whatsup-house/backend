package com.whatsuphouse.backend.domain.mailtemplate.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailTemplateUpdateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
    @Schema(example = "[Whats up House] 신청이 접수되었습니다 - {{모임명}}")
    private String subject;

    @NotBlank(message = "본문은 필수입니다.")
    @Schema(description = "평문 본문. {{변수}} 형태로 치환 변수를 사용합니다.")
    private String body;
}
