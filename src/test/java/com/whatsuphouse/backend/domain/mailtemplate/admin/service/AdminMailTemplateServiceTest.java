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
        assertThat(result.getVariables()).contains("이름", "모임명", "예약번호");
        assertThat(result.getBody()).contains("{{예약번호}}");
    }

    @Test
    @DisplayName("심사 승인 메일에는 계좌 없이 이용권 선택 링크만 포함된다")
    void getTemplate_approved_hasSelectionLinkOnly() {
        given(mailTemplateRepository.findByTemplateKey("APPLICATION_APPROVED")).willReturn(Optional.empty());

        MailTemplateDetailResponse result = adminMailTemplateService.getTemplate("APPLICATION_APPROVED");

        assertThat(result.getVariables()).contains("결제링크").doesNotContain("입금금액");
        assertThat(result.getBody()).contains("{{결제링크}}");
    }

    @Test
    @DisplayName("이용권 구매 요청 메일에 결제금액과 입금계좌가 포함된다")
    void getTemplate_ticketPurchaseRequested_hasPaymentInfo() {
        given(mailTemplateRepository.findByTemplateKey("TICKET_PURCHASE_REQUESTED")).willReturn(Optional.empty());

        MailTemplateDetailResponse result = adminMailTemplateService.getTemplate("TICKET_PURCHASE_REQUESTED");

        assertThat(result.getVariables()).contains("이용권명", "결제금액", "입금계좌", "결제링크");
        assertThat(result.getBody()).contains("{{결제금액}}", "{{입금계좌}}", "{{결제링크}}");
    }

    @Test
    @DisplayName("입금 완료 안내 템플릿(PAYMENT_CONFIRMED)을 조회할 수 있다 (KAN-242)")
    void getTemplate_paymentConfirmed_exists() {
        given(mailTemplateRepository.findByTemplateKey("PAYMENT_CONFIRMED")).willReturn(Optional.empty());

        MailTemplateDetailResponse result = adminMailTemplateService.getTemplate("PAYMENT_CONFIRMED");

        assertThat(result.getTemplateKey()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(result.getVariables()).contains("이름", "모임명", "예약번호");
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
                .subject("새 제목").body("새 본문 {{닉네임}}").build();

        MailTemplateDetailResponse result = adminMailTemplateService.updateTemplate("WELCOME", request);

        assertThat(result.getSubject()).isEqualTo("새 제목");
        assertThat(result.getBody()).isEqualTo("새 본문 {{닉네임}}");
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

        assertThat(result.getBody()).doesNotContain("{{이름}}");
        assertThat(result.getBody()).contains("홍길동");
    }

    @Test
    @DisplayName("미리보기는 초안 제목/본문이 주어지면 그것을 치환한다")
    void preview_usesDraftContent() {
        given(mailTemplateRepository.findByTemplateKey("WELCOME")).willReturn(Optional.empty());
        MailTemplatePreviewRequest request = MailTemplatePreviewRequest.builder()
                .subject("초안 {{닉네임}}")
                .body("초안 본문 {{닉네임}}")
                .variables(Map.of("닉네임", "테스터"))
                .build();

        MailTemplatePreviewResponse result = adminMailTemplateService.preview("WELCOME", request);

        assertThat(result.getSubject()).isEqualTo("초안 테스터");
        assertThat(result.getBody()).isEqualTo("초안 본문 테스터");
    }
}
