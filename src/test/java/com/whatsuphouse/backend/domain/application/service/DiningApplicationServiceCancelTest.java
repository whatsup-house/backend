package com.whatsuphouse.backend.domain.application.service;

import com.whatsuphouse.backend.domain.application.client.dto.response.DiningApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningCancelPreviewResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningCancelPreviewResponse.DepositOutcome;
import com.whatsuphouse.backend.domain.application.client.service.DiningApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.CancelWindow;
import com.whatsuphouse.backend.domain.application.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.form.client.service.FormService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.matching.service.DiningTableService;
import com.whatsuphouse.backend.domain.matching.service.MatchResolutionService;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/** 취소 시점 정책(마감 전·후·당일)과 보증금 처리, 취소 미리보기. (KAN-402) */
@ExtendWith(MockitoExtension.class)
class DiningApplicationServiceCancelTest {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationCandidateSessionRepository applicationCandidateSessionRepository;
    @Mock private ApplicationAnswerRepository applicationAnswerRepository;
    @Mock private TicketService ticketService;
    @Mock private MatchingService matchingService;
    @Mock private DiningAttendanceService diningAttendanceService;
    @Mock private DiningTableService diningTableService;
    @Mock private MatchResolutionService matchResolutionService;
    @Mock private FormService formService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DepositService depositService;

    @InjectMocks
    private DiningApplicationService diningApplicationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private User user;
    private Gathering randomTable;

    @BeforeEach
    void setUp() {
        user = User.builder().email("t@example.com").password("p").name("김철수").gender(Gender.MALE)
                .age(28).nickname("chulsoo").phone("01012345678").build();
        ReflectionTestUtils.setField(user, "id", userId);
        randomTable = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        ReflectionTestUtils.setField(randomTable, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("마감 전 취소: 1회권을 복원하고 참석은 CANCELED_EARLY, 보증금은 반환 요청")
    void cancel_beforeDeadline_ticketRestored() {
        Application application = confirmedApplication(sessionOn(3, null));
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));

        diningApplicationService.cancelApplication(applicationId, userId);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        then(ticketService).should().refundOneTicket(application);
        then(diningAttendanceService).should().cancelAttendance(applicationId, false);
        then(depositService).should().requestReturn(applicationId);
        then(depositService).should(never()).forfeit(any());
        assertThat(user.isDepositRequired()).isFalse();
        then(eventPublisher).should().publishEvent(any(ApplicationCancelledEvent.class));
    }

    @Test
    @DisplayName("마감 후(전날) 취소: 이용권 복원 없음, 참석 CANCELED_LATE, 보증금은 반환 요청, 제재 없음")
    void cancel_afterDeadline_noRestore_depositReturned() {
        Application application = confirmedApplication(sessionOn(1, null));
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));

        diningApplicationService.cancelApplication(applicationId, userId);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        then(ticketService).should(never()).refundOneTicket(any());
        then(diningAttendanceService).should().cancelAttendance(applicationId, true);
        then(depositService).should().requestReturn(applicationId);
        then(depositService).should(never()).forfeit(any());
        assertThat(user.isDepositRequired()).isFalse();
    }

    @Test
    @DisplayName("당일 취소: 이용권 복원 없음, 보증금 몰수, 회원은 보증금 대상이 된다")
    void cancel_sameDay_depositForfeited_userFlagged() {
        Application application = confirmedApplication(sessionOn(0, LocalTime.MAX));
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));

        diningApplicationService.cancelApplication(applicationId, userId);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        then(ticketService).should(never()).refundOneTicket(any());
        then(diningAttendanceService).should().cancelAttendance(applicationId, true);
        then(depositService).should().forfeit(applicationId);
        then(depositService).should(never()).requestReturn(any());
        assertThat(user.isDepositRequired()).isTrue();
    }

    @Test
    @DisplayName("회차가 이미 시작했으면 CANCEL_WINDOW_CLOSED, 아무것도 바꾸지 않는다")
    void cancel_sessionStarted_windowClosed() {
        Application application = confirmedApplication(sessionOn(0, LocalTime.MIDNIGHT));
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> diningApplicationService.cancelApplication(applicationId, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCEL_WINDOW_CLOSED);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        then(diningAttendanceService).should(never()).cancelAttendance(any(), anyBoolean());
        then(depositService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("미리보기(마감 전, 1회권 사용, 보증금 입금): 1회권 복원, 환불 없음, 보증금 반환, 제재 없음")
    void preview_beforeDeadline_countPass_ticketRestoredDepositReturned() {
        Application application = confirmedApplication(sessionOn(3, null));
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));
        given(ticketService.hasCountDeduction(applicationId)).willReturn(true);
        given(depositService.findByApplicationIds(List.of(applicationId)))
                .willReturn(Map.of(applicationId, paidDeposit(application)));

        DiningCancelPreviewResponse preview = diningApplicationService.previewCancel(applicationId, userId);

        assertThat(preview).isEqualTo(new DiningCancelPreviewResponse(
                CancelWindow.BEFORE_DEADLINE, true, false, DepositOutcome.RETURN, false));
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("미리보기(마감 전, 멤버십 사용): 복원할 1회권이 없으니 ticketRestored=false, 보증금 없으면 NONE")
    void preview_beforeDeadline_membership_noTicketRestore() {
        Application application = confirmedApplication(sessionOn(3, null));
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));
        given(ticketService.hasCountDeduction(applicationId)).willReturn(false);
        given(depositService.findByApplicationIds(List.of(applicationId))).willReturn(Map.of());

        DiningCancelPreviewResponse preview = diningApplicationService.previewCancel(applicationId, userId);

        assertThat(preview).isEqualTo(new DiningCancelPreviewResponse(
                CancelWindow.BEFORE_DEADLINE, false, false, DepositOutcome.NONE, false));
    }

    @Test
    @DisplayName("미리보기(당일, 보증금 입금): 복원 없음, 보증금 몰수, 제재")
    void preview_sameDay_forfeitAndSanction() {
        Application application = confirmedApplication(sessionOn(0, LocalTime.MAX));
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));
        given(depositService.findByApplicationIds(List.of(applicationId)))
                .willReturn(Map.of(applicationId, paidDeposit(application)));

        DiningCancelPreviewResponse preview = diningApplicationService.previewCancel(applicationId, userId);

        assertThat(preview).isEqualTo(new DiningCancelPreviewResponse(
                CancelWindow.SAME_DAY, false, false, DepositOutcome.FORFEIT, true));
        then(ticketService).should(never()).hasCountDeduction(any());
    }

    @Test
    @DisplayName("내 신청 목록: 최근에 취소한 신청도 조회하고 보증금 상태와 금액을 함께 준다")
    void listMyApplications_includesDepositAndRecentlyCancelled() {
        Application application = confirmedApplication(sessionOn(3, null));
        application.cancel();
        given(applicationRepository.findMyApplicationsIncludingRecentlyCancelled(
                eq(userId), eq(GatheringType.RANDOM_TABLE), eq(ApplicationStatus.CANCELLED), any()))
                .willReturn(List.of(application));
        Deposit deposit = paidDeposit(application);
        deposit.requestReturn();
        given(depositService.findByApplicationIds(List.of(applicationId))).willReturn(Map.of(applicationId, deposit));

        DiningApplicationListResponse response = diningApplicationService.listMyApplications(userId);

        DiningApplicationListResponse.Item item = response.getApplications().get(0);
        assertThat(item.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(item.getDeposit())
                .isEqualTo(new DiningApplicationListResponse.DepositInfo(DepositStatus.RETURN_PENDING, 50_000));
    }

    // 한국 날짜 기준 오늘 + days일 회차. startTime이 null이면 그날 0시 시작.
    private GatheringSession sessionOn(int days, LocalTime startTime) {
        GatheringSession session = GatheringSession.builder()
                .gathering(randomTable)
                .eventDate(LocalDate.now(KOREA).plusDays(days))
                .maxAttendees(6)
                .build();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(session, "startTime", startTime);
        return session;
    }

    private Application confirmedApplication(GatheringSession assigned) {
        Application application = Application.builder()
                .bookingNumber("WH261019-ABC123")
                .gathering(randomTable)
                .session(assigned)
                .user(user)
                .name(user.getName())
                .phone(user.getPhone())
                .build();
        ReflectionTestUtils.setField(application, "id", applicationId);
        application.confirm();
        return application;
    }

    private Deposit paidDeposit(Application application) {
        Deposit deposit = Deposit.pending(application, new RefundAccount("국민", "12345678901", "김철수"));
        deposit.markPaid();
        return deposit;
    }
}
