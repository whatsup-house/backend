package com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response;

import com.whatsuphouse.backend.domain.mailtemplate.enums.MailTemplateType;
import com.whatsuphouse.backend.domain.mailtemplate.service.MailContent;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MailTemplateResponse {

    private String templateKey;
    private String description;
    private String subject;

    public static MailTemplateResponse of(MailTemplateType type, MailContent content) {
        return MailTemplateResponse.builder()
                .templateKey(type.name())
                .description(type.getDescription())
                .subject(content.subject())
                .build();
    }
}
