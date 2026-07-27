package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.notification.event.*;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    private User user;
    private Gathering gathering;
    private Application application;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@test.com")
                .password("encoded")
                .name("테스트유저")
                .gender(Gender.MALE)
                .age(25)
                .nickname("testnick")
                .phone("01012345678")
                .build();

        gathering = Gathering.builder()
                .title("테스트 게더링")
                .eventDate(LocalDate.now().plusDays(7))
                .startTime(LocalTime.of(14, 0))
                .maxAttendees(10)
                .build();

        application = Application.builder()
                .bookingNumber("WH260428-TEST01")
                .gathering(gathering)
                .participant(Participant.member(user))
                .name(user.getName())
                .phone(user.getPhone())
                .build();
    }

    // ── onWelcome() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("WelcomeEvent 수신 시 notificationService.sendWelcome(user) 호출")
    void onWelcome_callsSendWelcome() {
        // given
        WelcomeEvent event = new WelcomeEvent(user);

        // when
        notificationEventListener.onWelcome(event);

        // then
        then(notificationService).should().sendWelcome(user);
    }

    // ── onApplicationPending() ────────────────────────────────────────────────

    @Test
    @DisplayName("ApplicationPendingEvent 수신 시 notificationService.sendApplicationPending(application) 호출")
    void onApplicationPending_callsSendApplicationPending() {
        // given
        ApplicationPendingEvent event = new ApplicationPendingEvent(application);

        // when
        notificationEventListener.onApplicationPending(event);

        // then
        then(notificationService).should().sendApplicationPending(application);
    }

    // ── onApplicationConfirmed() ──────────────────────────────────────────────

    @Test
    @DisplayName("ApplicationConfirmedEvent 수신 시 notificationService.sendApplicationConfirmed(application) 호출")
    void onApplicationConfirmed_callsSendApplicationConfirmed() {
        // given
        ApplicationConfirmedEvent event = new ApplicationConfirmedEvent(application);

        // when
        notificationEventListener.onApplicationConfirmed(event);

        // then
        then(notificationService).should().sendApplicationConfirmed(application);
    }

    // ── onApplicationCancelled() ──────────────────────────────────────────────

    @Test
    @DisplayName("ApplicationCancelledEvent 수신 시 notificationService.sendApplicationCancelled(application) 호출")
    void onApplicationCancelled_callsSendApplicationCancelled() {
        // given
        ApplicationCancelledEvent event = new ApplicationCancelledEvent(application);

        // when
        notificationEventListener.onApplicationCancelled(event);

        // then
        then(notificationService).should().sendApplicationCancelled(application);
    }

    // ── onApplicationAttended() ───────────────────────────────────────────────

    @Test
    @DisplayName("ApplicationAttendedEvent 수신 시 notificationService.sendApplicationAttended(application, mileageEarned, mileageBalance) 호출")
    void onApplicationAttended_callsSendApplicationAttended() {
        // given
        int mileageEarned = 1000;
        int mileageBalance = 2000;
        ApplicationAttendedEvent event = new ApplicationAttendedEvent(application, mileageEarned, mileageBalance);

        // when
        notificationEventListener.onApplicationAttended(event);

        // then
        then(notificationService).should().sendApplicationAttended(application, mileageEarned, mileageBalance);
    }

    // ── onGatheringCancelled() ────────────────────────────────────────────────

    @Test
    @DisplayName("GatheringCancelledEvent 수신 시 notificationService.sendGatheringCancelled(gathering, applications) 호출")
    void onGatheringCancelled_callsSendGatheringCancelled() {
        // given
        List<Application> applications = List.of(application);
        GatheringCancelledEvent event = new GatheringCancelledEvent(gathering, applications);

        // when
        notificationEventListener.onGatheringCancelled(event);

        // then
        then(notificationService).should().sendGatheringCancelled(gathering, applications);
    }
}
