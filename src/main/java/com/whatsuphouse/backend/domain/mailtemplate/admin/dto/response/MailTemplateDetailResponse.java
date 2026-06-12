package com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response;

import com.whatsuphouse.backend.domain.mailtemplate.enums.MailTemplateType;
import com.whatsuphouse.backend.domain.mailtemplate.service.MailContent;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MailTemplateDetailResponse {

    private String templateKey;
    private String description;
    private String subject;
    private String body;
    private List<String> variables; // 본문에서 사용 가능한 치환 변수 목록

    public static MailTemplateDetailResponse of(MailTemplateType type, MailContent content) {
        return MailTemplateDetailResponse.builder()
                .templateKey(type.name())
                .description(type.getDescription())
                .subject(content.subject())
                .body(content.body())
                .variables(type.getVariables())
                .build();
    }
}
