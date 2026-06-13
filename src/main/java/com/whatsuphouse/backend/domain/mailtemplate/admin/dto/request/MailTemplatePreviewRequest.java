package com.whatsuphouse.backend.domain.mailtemplate.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailTemplatePreviewRequest {

    @Schema(description = "미리볼 제목 초안. 생략 시 저장된(없으면 기본) 제목을 사용합니다.")
    private String subject;

    @Schema(description = "미리볼 본문 초안. 생략 시 저장된(없으면 기본) 본문을 사용합니다.")
    private String body;

    @Schema(description = "치환에 사용할 변수 값. 생략 시 템플릿별 샘플 값으로 미리보기합니다.")
    private Map<String, String> variables;
}
