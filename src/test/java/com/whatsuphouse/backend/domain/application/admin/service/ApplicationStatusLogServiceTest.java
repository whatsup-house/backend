package com.whatsuphouse.backend.domain.application.admin.service;

import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationStatusHistoryResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationStatusLog;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.application.enums.StatusLogField;
import com.whatsuphouse.backend.domain.application.event.ApplicationStatusChangedEvent;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationStatusLogRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.service.MatchResolutionService;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ApplicationStatusLogServiceTest {

    @Mock
    private ApplicationStatusLogRepository applicationStatusLogRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private DepositService depositService;
    @Mock
    private MatchResolutionService matchResolutionService;
    @Mock
    private UserService userService;

    @InjectMocks
    private ApplicationStatusLogService applicationStatusLogService;

    private Application application;

    @BeforeEach
    void setUp() {
        Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        application = Application.builder().bookingNumber("WH-1").gathering(gathering)
                .name("회원").phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.confirm();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void givenDeducted() {
        given(ticketService.findDeductionStatuses(anyCollection()))
                .willReturn(Map.of(application.getId(), TicketDeductionStatus.DEDUCTED));
        given(depositService.findByApplicationIds(anyCollection())).willReturn(Map.of());
        given(matchResolutionService.findOfferedResolutionIds(anyCollection())).willReturn(Map.of());
    }

    private ApplicationStatusLog log(StatusLogField field, String to, UUID actorId, LocalDateTime at) {
        ApplicationStatusLog log = ApplicationStatusLog.of(application.getId(), field, null, to, actorId, null);
        ReflectionTestUtils.setField(log, "createdAt", at);
        return log;
    }

    @Test
    @DisplayName("첫 이벤트: 기록이 없으면 신청·매칭·결제 상태를 from = null로 남기고, 인증이 없으면 시스템(actor null)")
    void onStatusChanged_firstEvent_recordsAllFieldsFromNull() {
        // given
        givenDeducted();
        given(applicationStatusLogRepository.findByApplicationIdOrderByCreatedAtDesc(application.getId())).willReturn(List.of());

        // when
        applicationStatusLogService.onStatusChanged(new ApplicationStatusChangedEvent(application, "신청"));

        // then
        ArgumentCaptor<ApplicationStatusLog> saved = ArgumentCaptor.forClass(ApplicationStatusLog.class);
        then(applicationStatusLogRepository).should(times(3)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(ApplicationStatusLog::getField, ApplicationStatusLog::getFromValue, ApplicationStatusLog::getToValue,
                        ApplicationStatusLog::getActorId, ApplicationStatusLog::getReason)
                .containsExactly(
                        tuple(StatusLogField.STATUS, null, "CONFIRMED", null, "신청"),
                        tuple(StatusLogField.MATCH_STATUS, null, "WAITING", null, "신청"),
                        tuple(StatusLogField.PAYMENT, null, "PAID", null, "신청"));
    }

    @Test
    @DisplayName("바뀐 항목만 직전 기록 값을 from으로 남기고, 요청한 회원을 행위자로 남긴다")
    void onStatusChanged_changedFieldOnly_recordsFromLastValueWithActor() {
        // given
        givenDeducted();
        LocalDateTime now = LocalDateTime.now();
        given(applicationStatusLogRepository.findByApplicationIdOrderByCreatedAtDesc(application.getId())).willReturn(List.of(
                log(StatusLogField.MATCH_STATUS, "CONFIRM_PENDING", null, now.minusMinutes(1)),
                log(StatusLogField.STATUS, "CONFIRMED", null, now.minusMinutes(5)),
                log(StatusLogField.MATCH_STATUS, "WAITING", null, now.minusMinutes(5)),
                log(StatusLogField.PAYMENT, "PAID", null, now.minusMinutes(5))));
        UUID adminId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(adminId, "admin@test.com", true), null, List.of()));
        application.changeMatchStatus(MatchStatus.REALLOCATING);

        // when
        applicationStatusLogService.onStatusChanged(new ApplicationStatusChangedEvent(application, "수동 조정 DISSOLVE"));

        // then
        ArgumentCaptor<ApplicationStatusLog> saved = ArgumentCaptor.forClass(ApplicationStatusLog.class);
        then(applicationStatusLogRepository).should().save(saved.capture());
        assertThat(saved.getValue().getField()).isEqualTo(StatusLogField.MATCH_STATUS);
        assertThat(saved.getValue().getFromValue()).isEqualTo("CONFIRM_PENDING");
        assertThat(saved.getValue().getToValue()).isEqualTo("REALLOCATING");
        assertThat(saved.getValue().getActorId()).isEqualTo(adminId);
        assertThat(saved.getValue().getReason()).isEqualTo("수동 조정 DISSOLVE");
    }

    @Test
    @DisplayName("바뀐 항목이 없으면 아무것도 남기지 않는다")
    void onStatusChanged_noChange_savesNothing() {
        // given
        givenDeducted();
        LocalDateTime now = LocalDateTime.now();
        given(applicationStatusLogRepository.findByApplicationIdOrderByCreatedAtDesc(application.getId())).willReturn(List.of(
                log(StatusLogField.STATUS, "CONFIRMED", null, now),
                log(StatusLogField.MATCH_STATUS, "WAITING", null, now),
                log(StatusLogField.PAYMENT, "PAID", null, now)));

        // when
        applicationStatusLogService.onStatusChanged(new ApplicationStatusChangedEvent(application, "재확인"));

        // then
        then(applicationStatusLogRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("사유는 200자로 자른다")
    void onStatusChanged_longReason_truncated() {
        // given
        givenDeducted();
        given(applicationStatusLogRepository.findByApplicationIdOrderByCreatedAtDesc(application.getId())).willReturn(List.of());

        // when
        applicationStatusLogService.onStatusChanged(new ApplicationStatusChangedEvent(application, "가".repeat(250)));

        // then
        ArgumentCaptor<ApplicationStatusLog> saved = ArgumentCaptor.forClass(ApplicationStatusLog.class);
        then(applicationStatusLogRepository).should(times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(log -> assertThat(log.getReason()).hasSize(200));
    }

    @Test
    @DisplayName("이력 조회: 최신순 그대로, 행위자 이름을 붙이고 시스템 처리는 null")
    void listHistory_mapsActorName() {
        // given
        UUID adminId = UUID.randomUUID();
        User admin = User.builder().email("admin@test.com").name("김운영").build();
        LocalDateTime now = LocalDateTime.now();
        ApplicationStatusLog byAdmin = log(StatusLogField.MATCH_STATUS, "REALLOCATING", adminId, now);
        ApplicationStatusLog bySystem = log(StatusLogField.STATUS, "CONFIRMED", null, now.minusDays(1));
        given(applicationRepository.existsById(application.getId())).willReturn(true);
        given(applicationStatusLogRepository.findByApplicationIdOrderByCreatedAtDesc(application.getId()))
                .willReturn(List.of(byAdmin, bySystem));
        given(userService.findUsersByIds(List.of(adminId))).willReturn(Map.of(adminId, admin));

        // when
        List<ApplicationStatusHistoryResponse> history = applicationStatusLogService.listHistory(application.getId());

        // then
        assertThat(history)
                .extracting(ApplicationStatusHistoryResponse::getField, ApplicationStatusHistoryResponse::getTo,
                        ApplicationStatusHistoryResponse::getActorName, ApplicationStatusHistoryResponse::getAt)
                .containsExactly(
                        tuple(StatusLogField.MATCH_STATUS, "REALLOCATING", "김운영", now),
                        tuple(StatusLogField.STATUS, "CONFIRMED", null, now.minusDays(1)));
    }

    @Test
    @DisplayName("없는 신청의 이력 조회 → CustomException")
    void listHistory_notFound_throwsCustomException() {
        // given
        UUID unknown = UUID.randomUUID();
        given(applicationRepository.existsById(unknown)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> applicationStatusLogService.listHistory(unknown)).isInstanceOf(CustomException.class);
    }
}
