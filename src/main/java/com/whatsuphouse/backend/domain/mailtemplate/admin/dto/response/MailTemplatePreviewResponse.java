package com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response;

import com.whatsuphouse.backend.domain.mailtemplate.service.MailContent;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MailTemplatePreviewResponse {

    private String subject;
    private String body;

    public static MailTemplatePreviewResponse from(MailContent content) {
        return MailTemplatePreviewResponse.builder()
                .subject(content.subject())
                .body(content.body())
                .build();
    }
}
