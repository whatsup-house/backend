package com.whatsuphouse.backend.domain.mailtemplate.admin.service;

import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.request.MailTemplatePreviewRequest;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.request.MailTemplateUpdateRequest;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response.MailTemplateDetailResponse;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response.MailTemplatePreviewResponse;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response.MailTemplateResponse;
import com.whatsuphouse.backend.domain.mailtemplate.entity.MailTemplate;
import com.whatsuphouse.backend.domain.mailtemplate.enums.MailTemplateType;
import com.whatsuphouse.backend.domain.mailtemplate.repository.MailTemplateRepository;
import com.whatsuphouse.backend.domain.mailtemplate.service.MailContent;
import com.whatsuphouse.backend.domain.mailtemplate.service.MailTemplateRenderer;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminMailTemplateService {

    private final MailTemplateRepository mailTemplateRepository;
    private final MailTemplateRenderer mailTemplateRenderer;

    // 전체 템플릿 목록. DB 오버라이드가 있으면 그 제목을, 없으면 기본 제목을 보여준다.
    public List<MailTemplateResponse> getTemplates() {
        return Arrays.stream(MailTemplateType.values())
                .map(type -> MailTemplateResponse.of(type, mailTemplateRenderer.resolve(type)))
                .toList();
    }

    public MailTemplateDetailResponse getTemplate(String templateKey) {
        MailTemplateType type = resolveType(templateKey);
        return MailTemplateDetailResponse.of(type, mailTemplateRenderer.resolve(type));
    }

    @Transactional
    public MailTemplateDetailResponse updateTemplate(String templateKey, MailTemplateUpdateRequest request) {
        MailTemplateType type = resolveType(templateKey);

        MailTemplate template = mailTemplateRepository.findByTemplateKey(type.name())
                .orElseGet(() -> mailTemplateRepository.save(MailTemplate.builder()
                        .templateKey(type.name())
                        .description(type.getDescription())
                        .subject(request.getSubject())
                        .body(request.getBody())
                        .build()));
        template.updateContent(request.getSubject(), request.getBody());

        return MailTemplateDetailResponse.of(type, new MailContent(template.getSubject(), template.getBody()));
    }

    // 미리보기. 초안 제목/본문이 주어지면 그것을, 없으면 저장본/기본값을 사용하고, 변수는 없으면 샘플로 채운다.
    public MailTemplatePreviewResponse preview(String templateKey, MailTemplatePreviewRequest request) {
        MailTemplateType type = resolveType(templateKey);
        MailContent base = mailTemplateRenderer.resolve(type);

        String subject = StringUtils.hasText(request.getSubject()) ? request.getSubject() : base.subject();
        String body = StringUtils.hasText(request.getBody()) ? request.getBody() : base.body();
        Map<String, String> variables = (request.getVariables() != null && !request.getVariables().isEmpty())
                ? request.getVariables()
                : type.getSampleVariables();

        MailContent rendered = new MailContent(
                MailTemplateRenderer.substitute(subject, variables),
                MailTemplateRenderer.substitute(body, variables));
        return MailTemplatePreviewResponse.from(rendered);
    }

    private MailTemplateType resolveType(String templateKey) {
        MailTemplateType type = MailTemplateType.fromKey(templateKey);
        if (type == null) {
            throw new CustomException(ErrorCode.MAIL_TEMPLATE_NOT_FOUND);
        }
        return type;
    }
}
