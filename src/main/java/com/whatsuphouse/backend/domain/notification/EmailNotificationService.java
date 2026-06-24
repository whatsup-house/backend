package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.mailtemplate.enums.MailTemplateType;
import com.whatsuphouse.backend.domain.mailtemplate.service.MailContent;
import com.whatsuphouse.backend.domain.mailtemplate.service.MailTemplateRenderer;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NotificationService의 이메일(SMTP) 구현체.
 *
 * [템플릿 - KAN-244]
 * 제목/본문은 mail_templates 테이블의 관리자 오버라이드(없으면 MailTemplateType 기본값)를
 * MailTemplateRenderer로 해석·치환합니다. 각 메서드는 {{변수}}에 채울 값 Map만 구성합니다.
 *
 * [비동기 처리 전략 - FR-NTF-09]
 * 모든 public 메서드에 @Async를 붙여 별도 스레드풀에서 실행됩니다.
 * 이메일 발송 실패(MailException)는 catch하여 경고 로그만 남기고
 * 예외를 재던지지 않으므로, 호출한 서비스의 트랜잭션에 영향을 주지 않습니다.
 *
 * [이벤트 기반 트리거]
 * 직접 호출되지 않고 NotificationEventListener가 도메인 이벤트를 받아
 * 이 서비스를 호출합니다. 덕분에 ApplicationService나 AdminApplicationService는
 * 알림 서비스를 직접 의존하지 않습니다.
 *
 * [비회원 처리]
 * Application.user 필드는 nullable입니다 (비회원 신청 허용).
 * resolveEmail()에서 user가 null이면 null을 반환하고, 각 메서드에서
 * 이를 확인해 이메일 발송을 건너뜁니다.
 *
 * [채널 교체]
 * 향후 SMS/알림톡으로 전환 시 SmsNotificationService를 구현하고
 * @Primary를 달면 호출부 변경 없이 채널을 교체할 수 있습니다 (FR-NTF-08).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService implements NotificationService {

    private final JavaMailSender mailSender;
    private final MailTemplateRenderer mailTemplateRenderer;

    /**
     * 발신자 이메일 주소.
     * application.yml의 notification.email.from 값으로 주입됩니다.
     * 예: noreply@whatsuphouse.com
     */
    @Value("${notification.email.from}")
    private String from;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * 회원가입 완료 환영 이메일.
     * 가입 축하 마일리지 1,000P 적립 안내를 포함합니다.
     * AuthService.register()에서 WelcomeEvent 발행 → NotificationEventListener 경유 → 이 메서드 호출.
     */
    @Override
    @Async("emailTaskExecutor")
    public void sendWelcome(User user) {
        MailContent mail = mailTemplateRenderer.render(MailTemplateType.WELCOME,
                Map.of("닉네임", user.getNickname()));
        send(user.getEmail(), mail.subject(), mail.body());
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendPasswordReset(User user, String resetUrl) {
        MailContent mail = mailTemplateRenderer.render(MailTemplateType.PASSWORD_RESET,
                Map.of("닉네임", user.getNickname(), "재설정링크", resetUrl));
        send(user.getEmail(), mail.subject(), mail.body());
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendGuestEmailVerification(String email, String code) {
        MailContent mail = mailTemplateRenderer.render(MailTemplateType.GUEST_EMAIL_VERIFICATION,
                Map.of("인증번호", code, "유효시간", "5분"));
        send(email, mail.subject(), mail.body());
    }

    /**
     * 신청 접수(PENDING) 확인 이메일 (FR-NTF-01, 02).
     * 예약번호를 포함하며, 회원은 마이페이지 신청 상세, 비회원은 예약번호 조회 화면으로 안내합니다.
     * 수신 이메일을 확인할 수 없는 신청은 발송을 건너뜁니다.
     */
    @Override
    @Async("emailTaskExecutor")
    public void sendApplicationPending(Application application) {
        String email = resolveEmail(application);
        if (email == null) return;

        MailContent mail = mailTemplateRenderer.render(MailTemplateType.APPLICATION_PENDING,
                applicationVariables(application));
        send(email, mail.subject(), mail.body());
    }

    /**
     * 신청 확정(CONFIRMED) 알림 이메일 (FR-NTF-03).
     * 관리자가 AdminApplicationService.changeStatus(CONFIRMED) 호출 시 트리거됩니다.
     */
    @Override
    @Async("emailTaskExecutor")
    public void sendApplicationConfirmed(Application application) {
        String email = resolveEmail(application);
        if (email == null) return;

        MailContent mail = mailTemplateRenderer.render(MailTemplateType.APPLICATION_CONFIRMED,
                applicationVariables(application));
        send(email, mail.subject(), mail.body());
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendApplicationApproved(Application application) {
        String email = resolveEmail(application);
        if (email == null) return;

        MailContent mail = mailTemplateRenderer.render(
                MailTemplateType.APPLICATION_APPROVED, applicationVariables(application));
        send(email, mail.subject(), mail.body());
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendTicketPurchaseRequested(Application application, TicketPass ticketPass) {
        String email = resolveEmail(application);
        if (email == null) return;

        Map<String, String> variables = applicationVariables(application);
        variables.put("이용권명", ticketPass.getProductLabel());
        variables.put("결제금액", String.format("%,d", ticketPass.getPurchaseAmount()));
        variables.put("입금계좌", "우리은행 1002-157-849052");
        variables.put("예금주", "와썹하우스");
        MailContent mail = mailTemplateRenderer.render(MailTemplateType.TICKET_PURCHASE_REQUESTED, variables);
        send(email, mail.subject(), mail.body());
    }

    /**
     * 입금 완료 안내 이메일 (KAN-242).
     * 관리자가 입금을 확인(체크)하면 ApplicationPaymentConfirmedEvent를 통해 호출됩니다.
     */
    @Override
    @Async("emailTaskExecutor")
    public void sendPaymentConfirmed(Application application) {
        String email = resolveEmail(application);
        if (email == null) return;

        Map<String, String> variables = applicationVariables(application);
        variables.put("확정링크", withPaymentConfirmed(variables.get("확정링크")));
        MailContent mail = mailTemplateRenderer.render(MailTemplateType.PAYMENT_CONFIRMED, variables);
        send(email, mail.subject(), mail.body());
    }

    /**
     * 신청 취소(CANCELLED) 알림 이메일 (FR-NTF-04).
     * 사용자 직접 취소(ApplicationService.cancel())와
     * 관리자 삭제(AdminApplicationService.deleteApplication()) 모두에서 발행되는
     * ApplicationCancelledEvent를 수신해 호출됩니다.
     */
    @Override
    @Async("emailTaskExecutor")
    public void sendApplicationCancelled(Application application) {
        String email = resolveEmail(application);
        if (email == null) return;

        MailContent mail = mailTemplateRenderer.render(MailTemplateType.APPLICATION_CANCELLED, Map.of(
                "이름", application.getName(),
                "모임명", application.getGathering().getTitle(),
                "예약번호", application.getBookingNumber()));
        send(email, mail.subject(), mail.body());
    }

    /**
     * 참석 처리(ATTENDED) 마일리지 적립 안내 이메일 (FR-NTF-05).
     * AdminApplicationService.changeStatus(ATTENDED) 내부에서 마일리지 적립 후
     * ApplicationAttendedEvent에 적립 금액과 잔액을 담아 발행합니다.
     *
     * @param mileageEarned  이번 참석으로 적립된 마일리지 (보통 1,000P)
     * @param mileageBalance 적립 후 현재 총 잔액
     */
    @Override
    @Async("emailTaskExecutor")
    public void sendApplicationAttended(Application application, int mileageEarned, int mileageBalance) {
        String email = resolveEmail(application);
        if (email == null) return;

        MailContent mail = mailTemplateRenderer.render(MailTemplateType.APPLICATION_ATTENDED, Map.of(
                "이름", application.getName(),
                "모임명", application.getGathering().getTitle(),
                "적립마일리지", String.format("%,d", mileageEarned),
                "마일리지잔액", String.format("%,d", mileageBalance)));
        send(email, mail.subject(), mail.body());
    }

    /**
     * 모임 전체 취소 일괄 발송 이메일 (FR-NTF-06).
     * AdminGatheringService에서 모임 상태를 CANCELLED로 변경할 때
     * PENDING/CONFIRMED 상태의 신청자 전원을 조회해 GatheringCancelledEvent로 발행합니다.
     * 각 신청자에게 개별 이메일을 발송하며, 한 건 실패해도 나머지는 계속 발송됩니다.
     *
     * @param applications PENDING 또는 CONFIRMED 상태인 신청 목록 (CANCELLED, ATTENDED 제외)
     */
    @Override
    @Async("emailTaskExecutor")
    public void sendGatheringCancelled(Gathering gathering, List<Application> applications) {
        for (Application application : applications) {
            String email = resolveEmail(application);
            if (email == null) continue;

            MailContent mail = mailTemplateRenderer.render(MailTemplateType.GATHERING_CANCELLED, Map.of(
                    "이름", application.getName(),
                    "모임명", gathering.getTitle(),
                    "예약번호", application.getBookingNumber()));
            send(email, mail.subject(), mail.body());
        }
    }

    /**
     * 신청 접수/확정 메일이 공유하는 변수 Map을 구성합니다.
     * startTime은 없을 수 있으므로 빈 문자열로 채워 포맷을 유지합니다.
     */
    private Map<String, String> applicationVariables(Application application) {
        Map<String, String> variables = new HashMap<>();
        variables.put("이름", application.getName());
        variables.put("모임명", application.getGathering().getTitle());
        variables.put("모임날짜", formatDate(application));
        variables.put("시작시간", formatTime(application));
        variables.put("예약번호", application.getBookingNumber());
        String encodedBookingNumber = URLEncoder.encode(application.getBookingNumber(), StandardCharsets.UTF_8);
        boolean member = application.getUser() != null;
        boolean randomTable = application.getGathering().getGatheringType() == GatheringType.RANDOM_TABLE;
        variables.put("조회경로", member
                ? frontendUrl + "/mypage/applications/" + application.getId()
                : frontendUrl + "/applications/check?bookingNumber=" + encodedBookingNumber);
        variables.put("결제링크", member
                ? frontendUrl + "/payments/random-table?applicationId=" + application.getId()
                : frontendUrl + "/payments/random-table?bookingNumber=" + encodedBookingNumber);
        variables.put("확정링크", frontendUrl + "/gatherings/" + application.getGathering().getId()
                + "/apply/confirmed" + (member ? "" : "?bookingNumber=" + encodedBookingNumber));
        variables.put("확정안내문구", randomTable
                ? "이용권 1회 사용이 완료되어 참가가 최종 확정되었습니다."
                : "참가가 승인되었습니다. 아래 링크에서 입금 계좌를 확인하고 입금해 주세요. 입금 확인 후 예약이 최종 확정됩니다.");
        variables.put("확정링크라벨", randomTable ? "참가 확정 확인" : "입금 안내 확인");
        return variables;
    }

    private String withPaymentConfirmed(String url) {
        return url + (url.contains("?") ? "&" : "?") + "payment=confirmed";
    }

    /**
     * 수신자 이메일 주소를 결정합니다.
     * 신청서에 저장된 이메일(회원=계정 이메일, 비회원=신청서 답변 이메일)을 우선 사용합니다.
     * 이메일이 없는 구버전 신청은 회원 계정 이메일로 폴백하고, 그래도 없으면 발송을 건너뜁니다.
     */
    private String resolveEmail(Application application) {
        if (application.getEmail() != null && !application.getEmail().isBlank()) {
            return application.getEmail();
        }
        if (application.getUser() != null) {
            return application.getUser().getEmail();
        }
        return null;
    }

    /**
     * 모임 날짜를 "yyyy년 MM월 dd일" 형식으로 포맷합니다.
     */
    private String formatDate(Application application) {
        return application.getGathering().getEventDate()
                .format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"));
    }

    /**
     * 모임 시작 시간을 "HH:mm" 형식으로 포맷합니다.
     * startTime이 없는 모임(종일 이벤트 등)은 빈 문자열을 반환합니다.
     */
    private String formatTime(Application application) {
        if (application.getGathering().getStartTime() == null) return "";
        return application.getGathering().getStartTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /**
     * 게더링 참가비를 천 단위 구분 문자열로 포맷합니다. 가격이 없으면 "0"을 반환합니다.
     */
    /**
     * 실제 이메일 발송을 처리하는 내부 메서드.
     *
     * MailException은 네트워크 오류, SMTP 인증 실패, 수신자 주소 오류 등
     * 발송 실패 시 Spring이 던지는 런타임 예외입니다.
     * 여기서 catch해 경고 로그만 남기고 예외를 재던지지 않음으로써
     * 이메일 실패가 신청/상태변경 트랜잭션을 롤백시키지 않도록 격리합니다 (FR-NTF-09).
     */
    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("이메일 발송 실패 [to={}] [subject={}]: {}", to, subject, e.getMessage());
        }
    }
}
