package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.client.service.ApplicationLookupTokenService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.whatsuphouse.backend.domain.mailtemplate.enums.MailTemplateType;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import com.whatsuphouse.backend.domain.mailtemplate.service.MailContent;
import com.whatsuphouse.backend.domain.mailtemplate.service.MailTemplateRenderer;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MailTemplateRenderer mailTemplateRenderer;

    @Mock
    private ApplicationLookupTokenService applicationLookupTokenService;

    @InjectMocks
    private EmailNotificationService emailNotificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailNotificationService, "from", "noreply@test.com");
        ReflectionTestUtils.setField(emailNotificationService, "frontendUrl", "http://localhost:3000");
        // 템플릿 렌더링은 별도 단위 테스트에서 검증한다. 여기서는 발송(send) 동작만 보므로 렌더 결과를 고정한다.
        lenient().when(mailTemplateRenderer.render(any(), any()))
                .thenReturn(new MailContent("제목", "본문"));
        lenient().when(applicationLookupTokenService.createToken("WH260428-TEST01"))
                .thenReturn("signed-token");
    }

    // ── sendWelcome() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("환영 이메일 - 렌더된 제목/본문이 수신자·발신자와 함께 메시지에 담겨 발송된다 (KAN-244)")
    void sendWelcome_rendersAndSendsContent() {
        // given: 렌더러가 특정 제목/본문을 반환하도록 지정
        given(mailTemplateRenderer.render(eq(MailTemplateType.WELCOME), any()))
                .willReturn(new MailContent("가입을 환영합니다", "안녕하세요 testnick님"));
        User user = buildUser("welcome@test.com");

        // when
        emailNotificationService.sendWelcome(user);

        // then: 렌더러는 WELCOME 타입 + 닉네임 변수로 호출되고,
        then(mailTemplateRenderer).should().render(MailTemplateType.WELCOME, Map.of("닉네임", "testnick"));
        // 렌더 결과가 그대로 발송 메시지의 제목/본문/수신자/발신자에 반영된다.
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        then(mailSender).should().send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("noreply@test.com");
        assertThat(sent.getTo()).containsExactly("welcome@test.com");
        assertThat(sent.getSubject()).isEqualTo("가입을 환영합니다");
        assertThat(sent.getText()).isEqualTo("안녕하세요 testnick님");
    }

    @Test
    @DisplayName("환영 이메일 - JavaMailSender.send()가 호출된다")
    void sendWelcome_success_sendCalled() {
        // given
        User user = buildUser("welcome@test.com");

        // when
        emailNotificationService.sendWelcome(user);

        // then
        then(mailSender).should().send(any(SimpleMailMessage.class));
    }

    // ── sendApplicationPending() ──────────────────────────────────────────────

    @Test
    @DisplayName("신청 접수 이메일 - 회원이면 마이페이지 신청 상세 링크를 포함한다")
    void sendApplicationPending_member_containsMyApplicationDetailLink() {
        // given
        User user = buildUser("member@test.com");
        Gathering gathering = buildGathering();
        Application application = buildApplication(user, gathering);

        // when
        emailNotificationService.sendApplicationPending(application);

        // then
        then(mailSender).should().send(any(SimpleMailMessage.class));
        then(mailTemplateRenderer).should().render(eq(MailTemplateType.APPLICATION_PENDING), argThat(vars ->
                "WH260428-TEST01".equals(vars.get("예약번호"))
                        && vars.get("조회경로").equals(
                        "http://localhost:3000/mypage/applications/00000000-0000-0000-0000-000000000101")));
    }

    @Test
    @DisplayName("신청 접수 이메일 - 비회원이면 인증 없이 신청 완료 화면으로 가는 토큰 링크를 포함한다")
    void sendApplicationPending_guest_containsDirectCompleteLink() {
        Gathering gathering = buildGathering();
        Application application = buildApplication(null, gathering);
        ReflectionTestUtils.setField(application, "email", "guest@test.com");

        emailNotificationService.sendApplicationPending(application);

        then(mailTemplateRenderer).should().render(eq(MailTemplateType.APPLICATION_PENDING), argThat(vars ->
                vars.get("조회경로").equals(
                        "http://localhost:3000/gatherings/00000000-0000-0000-0000-000000000201/apply/complete?bookingNumber=WH260428-TEST01&token=signed-token")));
        then(mailSender).should().send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("심사 승인 이메일에는 결제 페이지 링크가 포함된다")
    void sendApplicationApproved_containsPaymentLink() {
        Application application = buildApplication(buildUser("approved@test.com"), buildGathering());

        emailNotificationService.sendApplicationApproved(application);

        then(mailTemplateRenderer).should().render(eq(MailTemplateType.APPLICATION_APPROVED), argThat(vars ->
                vars.get("결제링크").equals(
                        "http://localhost:3000/payments/random-table?applicationId=00000000-0000-0000-0000-000000000101")));
        then(mailSender).should().send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("이용권 구매 요청 메일에는 상품·금액·계좌·상태 링크가 포함된다")
    void sendTicketPurchaseRequested_containsPaymentDetails() {
        User user = buildUser("ticket@test.com");
        Application application = buildApplication(user, buildGathering());
        TicketPass pass = TicketPass.builder()
                .participant(Participant.member(user))
                .product(TicketProduct.RANDOM_TABLE_FOUR)
                .build();

        emailNotificationService.sendTicketPurchaseRequested(application, pass);

        then(mailTemplateRenderer).should().render(eq(MailTemplateType.TICKET_PURCHASE_REQUESTED), argThat(vars ->
                "우연한 식탁 4회권".equals(vars.get("이용권명"))
                        && "18,000".equals(vars.get("결제금액"))
                        && "우리은행 1002-157-849052".equals(vars.get("입금계좌"))
                        && vars.get("결제링크").contains("applicationId=00000000-0000-0000-0000-000000000101")));
        then(mailSender).should().send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("비회원 이메일 인증번호와 유효시간을 발송한다")
    void sendGuestEmailVerification_sendsCode() {
        emailNotificationService.sendGuestEmailVerification("guest@test.com", "123456");

        then(mailTemplateRenderer).should().render(MailTemplateType.GUEST_EMAIL_VERIFICATION,
                Map.of("인증번호", "123456", "유효시간", "5분"));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        then(mailSender).should().send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("guest@test.com");
    }

    @Test
    @DisplayName("신청 접수 이메일 - 비회원(user == null)이면 send 호출되지 않음")
    void sendApplicationPending_guest_sendNotCalled() {
        // given
        Gathering gathering = buildGathering();
        Application application = buildApplication(null, gathering);

        // when
        emailNotificationService.sendApplicationPending(application);

        // then
        then(mailSender).shouldHaveNoInteractions();
    }

    // ── sendApplicationConfirmed() ────────────────────────────────────────────

    @Test
    @DisplayName("신청 확정 이메일 - send 호출 검증")
    void sendApplicationConfirmed_member_sendCalled() {
        // given
        User user = buildUser("confirmed@test.com");
        Gathering gathering = buildGathering();
        Application application = buildApplication(user, gathering);

        // when
        emailNotificationService.sendApplicationConfirmed(application);

        // then
        then(mailSender).should().send(any(SimpleMailMessage.class));
    }

    // ── sendApplicationCancelled() ────────────────────────────────────────────

    @Test
    @DisplayName("신청 취소 이메일 - send 호출 검증")
    void sendApplicationCancelled_member_sendCalled() {
        // given
        User user = buildUser("cancelled@test.com");
        Gathering gathering = buildGathering();
        Application application = buildApplication(user, gathering);

        // when
        emailNotificationService.sendApplicationCancelled(application);

        // then
        then(mailSender).should().send(any(SimpleMailMessage.class));
    }

    // ── sendApplicationAttended() ─────────────────────────────────────────────

    @Test
    @DisplayName("참석 처리 이메일 - send 호출 검증")
    void sendApplicationAttended_member_sendCalled() {
        // given
        User user = buildUser("attended@test.com");
        Gathering gathering = buildGathering();
        Application application = buildApplication(user, gathering);

        // when
        emailNotificationService.sendApplicationAttended(application, 1000, 2000);

        // then
        then(mailSender).should().send(any(SimpleMailMessage.class));
    }

    // ── sendGatheringCancelled() ──────────────────────────────────────────────

    @Test
    @DisplayName("모임 취소 일괄 이메일 - 회원 신청 2건이면 send 2회 호출")
    void sendGatheringCancelled_twoMembers_sendCalledTwice() {
        // given
        Gathering gathering = buildGathering();
        User user1 = buildUser("user1@test.com");
        User user2 = buildUser("user2@test.com");
        Application app1 = buildApplication(user1, gathering);
        Application app2 = buildApplication(user2, gathering);

        // when
        emailNotificationService.sendGatheringCancelled(gathering, List.of(app1, app2));

        // then
        then(mailSender).should(times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("모임 취소 일괄 이메일 - 비회원 포함 시 비회원은 건너뜀")
    void sendGatheringCancelled_withGuest_guestSkipped() {
        // given
        Gathering gathering = buildGathering();
        User member = buildUser("member@test.com");
        Application memberApp = buildApplication(member, gathering);
        Application guestApp = buildApplication(null, gathering);

        // when
        emailNotificationService.sendGatheringCancelled(gathering, List.of(memberApp, guestApp));

        // then — 회원 1건만 발송, 비회원은 건너뜀
        then(mailSender).should(times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("신청 확정 이메일 - 비회원(user == null)이면 send 호출되지 않음")
    void sendApplicationConfirmed_guest_sendNotCalled() {
        // given
        Gathering gathering = buildGathering();
        Application application = buildApplication(null, gathering);

        // when
        emailNotificationService.sendApplicationConfirmed(application);

        // then
        then(mailSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("신청 취소 이메일 - 비회원(user == null)이면 send 호출되지 않음")
    void sendApplicationCancelled_guest_sendNotCalled() {
        // given
        Gathering gathering = buildGathering();
        Application application = buildApplication(null, gathering);

        // when
        emailNotificationService.sendApplicationCancelled(application);

        // then
        then(mailSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("참석 처리 이메일 - 비회원(user == null)이면 send 호출되지 않음")
    void sendApplicationAttended_guest_sendNotCalled() {
        // given
        Gathering gathering = buildGathering();
        Application application = buildApplication(null, gathering);

        // when
        emailNotificationService.sendApplicationAttended(application, 1000, 2000);

        // then
        then(mailSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("모임 취소 일괄 이메일 - 대상 신청 없으면 send 호출되지 않음")
    void sendGatheringCancelled_emptyList_sendNotCalled() {
        // given
        Gathering gathering = buildGathering();

        // when
        emailNotificationService.sendGatheringCancelled(gathering, List.of());

        // then
        then(mailSender).shouldHaveNoInteractions();
    }

    // ── MailException 격리 ────────────────────────────────────────────────────

    @Test
    @DisplayName("mailSender.send()가 MailException을 던져도 예외가 외부로 전파되지 않는다")
    void send_mailException_doesNotThrow() {
        // given
        User user = buildUser("error@test.com");
        willThrow(new MailSendException("SMTP 오류")).given(mailSender).send(any(SimpleMailMessage.class));

        // when & then
        assertThatNoException().isThrownBy(() -> emailNotificationService.sendWelcome(user));
    }

    @Test
    @DisplayName("모임 취소 일괄 발송 중 한 건이 MailException이어도 나머지가 발송된다")
    void sendGatheringCancelled_oneMailException_othersSent() {
        // given
        Gathering gathering = buildGathering();
        User user1 = buildUser("user1@test.com");
        User user2 = buildUser("user2@test.com");
        Application app1 = buildApplication(user1, gathering);
        Application app2 = buildApplication(user2, gathering);

        willThrow(new MailSendException("SMTP 오류"))
                .willDoNothing()
                .given(mailSender).send(any(SimpleMailMessage.class));

        // when & then
        assertThatNoException().isThrownBy(() ->
                emailNotificationService.sendGatheringCancelled(gathering, List.of(app1, app2)));
        then(mailSender).should(times(2)).send(any(SimpleMailMessage.class));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildUser(String email) {
        return User.builder()
                .email(email)
                .password("encoded")
                .name("테스트유저")
                .gender(Gender.MALE)
                .age(25)
                .nickname("testnick")
                .phone("01012345678")
                .build();
    }

    private Gathering buildGathering() {
        Gathering gathering = Gathering.builder()
                .title("테스트 게더링")
                .eventDate(LocalDate.now().plusDays(7))
                .startTime(LocalTime.of(14, 0))
                .maxAttendees(10)
                .build();
        ReflectionTestUtils.setField(gathering, "id", UUID.fromString("00000000-0000-0000-0000-000000000201"));
        return gathering;
    }

    private Application buildApplication(User user, Gathering gathering) {
        Application application = Application.builder()
                .bookingNumber("WH260428-TEST01")
                .gathering(gathering)
                .participant(user != null ? Participant.member(user) : Participant.guest("비회원", "g@test.com", "01099999999"))
                .name(user != null ? user.getName() : "비회원")
                .phone(user != null ? user.getPhone() : "01099999999")
                .build();
        ReflectionTestUtils.setField(application, "id", UUID.fromString("00000000-0000-0000-0000-000000000101"));
        return application;
    }
}
