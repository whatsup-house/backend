package com.whatsuphouse.backend.domain.mailtemplate.service;

import com.whatsuphouse.backend.domain.mailtemplate.entity.MailTemplate;
import com.whatsuphouse.backend.domain.mailtemplate.enums.MailTemplateType;
import com.whatsuphouse.backend.domain.mailtemplate.repository.MailTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 메일 템플릿을 해석하고 변수를 치환한다. (KAN-244)
 * DB에 관리자 오버라이드가 있으면 그것을, 없으면 MailTemplateType의 기본값을 사용한다.
 */
@Component
@RequiredArgsConstructor
public class MailTemplateRenderer {

    private final MailTemplateRepository mailTemplateRepository;

    /**
     * DB 오버라이드(없으면 기본값)의 원본 제목/본문을 반환한다. (치환 전)
     */
    public MailContent resolve(MailTemplateType type) {
        MailTemplate override = mailTemplateRepository.findByTemplateKey(type.name()).orElse(null);
        String subject = override != null ? override.getSubject() : type.getDefaultSubject();
        String body = override != null ? override.getBody() : type.getDefaultBody();
        return new MailContent(subject, body);
    }

    /**
     * 해석된 템플릿에 변수를 치환한 발송용 제목/본문을 반환한다.
     */
    public MailContent render(MailTemplateType type, Map<String, String> variables) {
        MailContent raw = resolve(type);
        return new MailContent(substitute(raw.subject(), variables), substitute(raw.body(), variables));
    }

    /**
     * {{key}} 형태의 플레이스홀더를 변수 값으로 단순 치환한다.
     * 등록된 변수명만 치환하므로, 본문 내 다른 텍스트(예: 브랜드 [Whats up House])는 그대로 유지된다.
     */
    public static String substitute(String text, Map<String, String> variables) {
        if (text == null) {
            return null;
        }
        String result = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        return result;
    }
}
