package com.whatsuphouse.backend.domain.mailtemplate.service;

import com.whatsuphouse.backend.domain.mailtemplate.entity.MailTemplate;
import com.whatsuphouse.backend.domain.mailtemplate.enums.MailTemplateType;
import com.whatsuphouse.backend.domain.mailtemplate.repository.MailTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MailTemplateRendererTest {

    @Mock
    private MailTemplateRepository mailTemplateRepository;

    @InjectMocks
    private MailTemplateRenderer renderer;

    @Test
    @DisplayName("{{변수}} 플레이스홀더를 값으로 치환한다")
    void substitute_replacesPlaceholders() {
        String result = MailTemplateRenderer.substitute(
                "안녕하세요 {{name}}님, 잔액 {{balance}}P",
                Map.of("name", "홍길동", "balance", "2,000"));

        assertThat(result).isEqualTo("안녕하세요 홍길동님, 잔액 2,000P");
    }

    @Test
    @DisplayName("DB 오버라이드가 없으면 기본 템플릿을 사용한다")
    void render_usesDefault_whenNoOverride() {
        given(mailTemplateRepository.findByTemplateKey("WELCOME")).willReturn(Optional.empty());

        MailContent content = renderer.render(MailTemplateType.WELCOME, Map.of("nickname", "홍길동"));

        assertThat(content.subject()).isEqualTo("[Whats up House] 가입을 환영합니다!");
        assertThat(content.body()).contains("홍길동");
        assertThat(content.body()).doesNotContain("{{");
    }

    @Test
    @DisplayName("DB 오버라이드가 있으면 관리자가 수정한 내용을 사용한다")
    void render_usesDbOverride_whenPresent() {
        MailTemplate override = MailTemplate.builder()
                .templateKey("WELCOME")
                .description("회원가입 환영")
                .subject("커스텀 제목 {{nickname}}")
                .body("커스텀 본문 {{nickname}}")
                .build();
        given(mailTemplateRepository.findByTemplateKey("WELCOME")).willReturn(Optional.of(override));

        MailContent content = renderer.render(MailTemplateType.WELCOME, Map.of("nickname", "길동"));

        assertThat(content.subject()).isEqualTo("커스텀 제목 길동");
        assertThat(content.body()).isEqualTo("커스텀 본문 길동");
    }
}
