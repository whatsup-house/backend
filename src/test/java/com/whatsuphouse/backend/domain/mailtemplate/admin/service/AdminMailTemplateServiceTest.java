package com.whatsuphouse.backend.domain.mailtemplate.admin.service;

import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.request.MailTemplatePreviewRequest;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.request.MailTemplateUpdateRequest;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response.MailTemplateDetailResponse;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response.MailTemplatePreviewResponse;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response.MailTemplateResponse;
import com.whatsuphouse.backend.domain.mailtemplate.entity.MailTemplate;
import com.whatsuphouse.backend.domain.mailtemplate.enums.MailTemplateType;
import com.whatsuphouse.backend.domain.mailtemplate.repository.MailTemplateRepository;
import com.whatsuphouse.backend.domain.mailtemplate.service.MailTemplateRenderer;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminMailTemplateServiceTest {

    @Mock
    private MailTemplateRepository mailTemplateRepository;

    private AdminMailTemplateService adminMailTemplateService;

    @BeforeEach
    void setUp() {
        MailTemplateRenderer renderer = new MailTemplateRenderer(mailTemplateRepository);
        adminMailTemplateService = new AdminMailTemplateService(mailTemplateRepository, renderer);
    }

    @Test
    @DisplayName("전체 템플릿 목록은 정의된 모든 타입을 반환한다")
    void getTemplates_returnsAllTypes() {
        given(mailTemplateRepository.findByTemplateKey(anyString())).willReturn(Optional.empty());

        List<MailTemplateResponse> result = adminMailTemplateService.getTemplates();

        assertThat(result).hasSize(MailTemplateType.values().length);
    }

    @Test
    @DisplayName("상세 조회 시 기본 본문과 사용 가능 변수를 반환한다")
    void getTemplate_default_returnsVariables() {
        given(mailTemplateRepository.findByTemplateKey("APPLICATION_PENDING")).willReturn(Optional.empty());

        MailTemplateDetailResponse result = adminMailTemplateService.getTemplate("APPLICATION_PENDING");

        assertThat(result.getTemplateKey()).isEqualTo("APPLICATION_PENDING");
        assertThat(result.getVariables()).contains("name", "gatheringTitle", "bookingNumber");
        assertThat(result.getBody()).contains("{{bookingNumber}}");
    }

    @Test
    @DisplayName("존재하지 않는 템플릿 키 조회 시 예외 발생")
    void getTemplate_unknownKey_throwsException() {
        assertThatThrownBy(() -> adminMailTemplateService.getTemplate("UNKNOWN_KEY"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAIL_TEMPLATE_NOT_FOUND);
    }

    @Test
    @DisplayName("오버라이드가 없으면 새 행을 저장한다")
    void updateTemplate_createsWhenAbsent() {
        given(mailTemplateRepository.findByTemplateKey("WELCOME")).willReturn(Optional.empty());
        given(mailTemplateRepository.save(any(MailTemplate.class))).willAnswer(inv -> inv.getArgument(0));
        MailTemplateUpdateRequest request = MailTemplateUpdateRequest.builder()
                .subject("새 제목").body("새 본문 {{nickname}}").build();

        MailTemplateDetailResponse result = adminMailTemplateService.updateTemplate("WELCOME", request);

        assertThat(result.getSubject()).isEqualTo("새 제목");
        assertThat(result.getBody()).isEqualTo("새 본문 {{nickname}}");
    }

    @Test
    @DisplayName("오버라이드가 있으면 기존 행 내용을 갱신한다")
    void updateTemplate_updatesWhenPresent() {
        MailTemplate existing = MailTemplate.builder()
                .templateKey("WELCOME").description("회원가입 환영")
                .subject("이전 제목").body("이전 본문").build();
        given(mailTemplateRepository.findByTemplateKey("WELCOME")).willReturn(Optional.of(existing));
        MailTemplateUpdateRequest request = MailTemplateUpdateRequest.builder()
                .subject("바뀐 제목").body("바뀐 본문").build();

        MailTemplateDetailResponse result = adminMailTemplateService.updateTemplate("WELCOME", request);

        assertThat(result.getSubject()).isEqualTo("바뀐 제목");
        assertThat(existing.getBody()).isEqualTo("바뀐 본문");
    }

    @Test
    @DisplayName("미리보기는 변수 미지정 시 샘플 값으로 치환한다")
    void preview_usesSampleVariables_whenNotProvided() {
        given(mailTemplateRepository.findByTemplateKey("APPLICATION_PENDING")).willReturn(Optional.empty());

        MailTemplatePreviewResponse result = adminMailTemplateService.preview(
                "APPLICATION_PENDING", new MailTemplatePreviewRequest());

        assertThat(result.getBody()).doesNotContain("{{");
        assertThat(result.getBody()).contains("홍길동");
    }

    @Test
    @DisplayName("미리보기는 초안 제목/본문이 주어지면 그것을 치환한다")
    void preview_usesDraftContent() {
        given(mailTemplateRepository.findByTemplateKey("WELCOME")).willReturn(Optional.empty());
        MailTemplatePreviewRequest request = MailTemplatePreviewRequest.builder()
                .subject("초안 {{nickname}}")
                .body("초안 본문 {{nickname}}")
                .variables(Map.of("nickname", "테스터"))
                .build();

        MailTemplatePreviewResponse result = adminMailTemplateService.preview("WELCOME", request);

        assertThat(result.getSubject()).isEqualTo("초안 테스터");
        assertThat(result.getBody()).isEqualTo("초안 본문 테스터");
    }
}
