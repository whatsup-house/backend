package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * NotificationService의 이메일(SMTP) 구현체.
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

    /**
     * 발신자 이메일 주소.
     * application.yml의 notification.email.from 값으로 주입됩니다.
     * 예: noreply@whatsuphouse.com
     */
    @Value("${notification.email.from}")
    private String from;

    /**
     * 회원가입 완료 환영 이메일.
     * 가입 축하 마일리지 1,000P 적립 안내를 포함합니다.
     * AuthService.register()에서 WelcomeEvent 발행 → NotificationEventListener 경유 → 이 메서드 호출.
     */
    @Override
    @Async
    public void sendWelcome(User user) {
        String subject = "[Whats up House] 가입을 환영합니다!";
        String body = String.format("""
                안녕하세요, %s님!

                Whats up House에 가입해 주셔서 감사합니다.
                가입 축하 마일리지 1,000P가 적립되었습니다.

                다양한 모임에서 새로운 사람들을 만나보세요.
                """, user.getNickname());
        send(user.getEmail(), subject, body);
    }

    /**
     * 신청 접수(PENDING) 확인 이메일 (FR-NTF-01, 02).
     * 예약번호를 포함하며, 신청자가 나중에 상태 조회(/api/applications/check)에 사용할 수 있습니다.
     * 비회원(user == null)은 이메일 주소가 없으므로 발송을 건너뜁니다.
     */
    @Override
    @Async
    public void sendApplicationPending(Application application) {
        String email = resolveEmail(application);
        if (email == null) return;

        String subject = "[Whats up House] 신청이 접수되었습니다 - " + application.getGathering().getTitle();
        String body = String.format("""
                안녕하세요, %s님!

                모임 신청이 정상적으로 접수되었습니다.

                모임명: %s
                일시: %s %s
                예약번호: %s

                신청 결과는 별도 이메일로 안내해 드리겠습니다.
                """,
                application.getName(),
                application.getGathering().getTitle(),
                formatDate(application),
                formatTime(application),
                application.getBookingNumber());
        send(email, subject, body);
    }

    /**
     * 신청 확정(CONFIRMED) 알림 이메일 (FR-NTF-03).
     * 관리자가 AdminApplicationService.changeStatus(CONFIRMED) 호출 시 트리거됩니다.
     */
    @Override
    @Async
    public void sendApplicationConfirmed(Application application) {
        String email = resolveEmail(application);
        if (email == null) return;

        String subject = "[Whats up House] 신청이 확정되었습니다 - " + application.getGathering().getTitle();
        String body = String.format("""
                안녕하세요, %s님!

                모임 참가가 확정되었습니다. 당일 꼭 참석해 주세요!

                모임명: %s
                일시: %s %s
                예약번호: %s
                """,
                application.getName(),
                application.getGathering().getTitle(),
                formatDate(application),
                formatTime(application),
                application.getBookingNumber());
        send(email, subject, body);
    }

    /**
     * 신청 취소(CANCELLED) 알림 이메일 (FR-NTF-04).
     * 사용자 직접 취소(ApplicationService.cancel())와
     * 관리자 삭제(AdminApplicationService.deleteApplication()) 모두에서 발행되는
     * ApplicationCancelledEvent를 수신해 호출됩니다.
     */
    @Override
    @Async
    public void sendApplicationCancelled(Application application) {
        String email = resolveEmail(application);
        if (email == null) return;

        String subject = "[Whats up House] 신청이 취소되었습니다 - " + application.getGathering().getTitle();
        String body = String.format("""
                안녕하세요, %s님!

                아래 신청이 취소 처리되었습니다.

                모임명: %s
                예약번호: %s

                다음 모임에서 만나뵐 수 있기를 기대합니다.
                """,
                application.getName(),
                application.getGathering().getTitle(),
                application.getBookingNumber());
        send(email, subject, body);
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
    @Async
    public void sendApplicationAttended(Application application, int mileageEarned, int mileageBalance) {
        String email = resolveEmail(application);
        if (email == null) return;

        String subject = "[Whats up House] 참석이 확인되었습니다 - " + application.getGathering().getTitle();
        String body = String.format("""
                안녕하세요, %s님!

                모임 참석이 확인되어 마일리지가 적립되었습니다.

                모임명: %s
                적립 마일리지: +%,dP
                현재 잔액: %,dP
                """,
                application.getName(),
                application.getGathering().getTitle(),
                mileageEarned,
                mileageBalance);
        send(email, subject, body);
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
    @Async
    public void sendGatheringCancelled(Gathering gathering, List<Application> applications) {
        for (Application application : applications) {
            String email = resolveEmail(application);
            if (email == null) continue;

            String subject = "[Whats up House] 모임이 취소되었습니다 - " + gathering.getTitle();
            String body = String.format("""
                    안녕하세요, %s님!

                    신청하셨던 모임이 취소되었습니다.

                    모임명: %s
                    예약번호: %s

                    불편을 드려 죄송합니다. 다른 모임도 많이 확인해 보세요.
                    """,
                    application.getName(),
                    gathering.getTitle(),
                    application.getBookingNumber());
            send(email, subject, body);
        }
    }

    /**
     * 수신자 이메일 주소를 결정합니다.
     * 회원 신청이면 users 테이블의 email을 사용하고,
     * 비회원 신청(user == null)이면 null을 반환해 발송을 건너뜁니다.
     */
    private String resolveEmail(Application application) {
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
