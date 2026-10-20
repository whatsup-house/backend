package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.dto.request.ResolutionChooseRequest;
import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import com.whatsuphouse.backend.domain.matching.entity.MatchResolution;
import com.whatsuphouse.backend.domain.matching.enums.ResolutionChoice;
import com.whatsuphouse.backend.domain.matching.enums.ResolutionStatus;
import com.whatsuphouse.backend.domain.matching.repository.MatchResolutionRepository;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.DiningResolutionEvent;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class MatchResolutionServiceTest {

    @Mock
    private MatchResolutionRepository matchResolutionRepository;
    @Mock
    private GatheringService gatheringService;
    @Mock
    private AdminApplicationService adminApplicationService;
    @Mock
    private TicketService ticketService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private DepositService depositService;

    @InjectMocks
    private MatchResolutionService matchResolutionService;

    private final UUID userId = UUID.randomUUID();
    private Gathering gathering;
    private GatheringSession wished;
    private GatheringSession alternative;
    private Application application;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matchResolutionService, "respondHours", 48L);
        gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        ReflectionTestUtils.setField(gathering, "id", UUID.randomUUID());
        wished = session(3, LocalDateTime.now().minusHours(1));
        alternative = session(10, LocalDateTime.now().plusDays(5));
        User user = User.builder().email("u@example.com").password("pw").name("회원").gender(Gender.MALE).age(30)
                .nickname("회원").build();
        ReflectionTestUtils.setField(user, "id", userId);
        application = Application.builder().bookingNumber("WH-1").gathering(gathering).user(user)
                .name("회원").phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.confirm();
        application.changeMatchStatus(MatchStatus.ALTERNATIVE_OFFERED);
    }

    private GatheringSession session(int daysLater, LocalDateTime matchRunAt) {
        GatheringSession s = GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().plusDays(daysLater)).maxAttendees(12).build();
        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
        s.changeMatchingRules(matchRunAt, null, null, null, null, null);
        return s;
    }

    private MatchResolution offered(List<UUID> sessionIds) {
        MatchResolution resolution = MatchResolution.offer(application, sessionIds, LocalDateTime.now().plusHours(48));
        ReflectionTestUtils.setField(resolution, "id", UUID.randomUUID());
        given(matchResolutionRepository.findByIdForUpdate(resolution.getId())).willReturn(Optional.of(resolution));
        return resolution;
    }

    private void givenSessions() {
        given(gatheringService.listSessionsByGathering(gathering.getId())).willReturn(List.of(wished, alternative));
    }

    private List<NotificationType> publishedTypes(int count) {
        ArgumentCaptor<DiningResolutionEvent> events = ArgumentCaptor.forClass(DiningResolutionEvent.class);
        then(eventPublisher).should(times(count)).publishEvent(events.capture());
        return events.getAllValues().stream().map(DiningResolutionEvent::getType).toList();
    }

    // ── 제안 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("제안: 고르지 않은 모집 중 회차를 제안하고 48시간 기한 + DINING_ALTERNATIVE_OFFERED")
    void offer_withAlternative() {
        givenSessions();

        matchResolutionService.offerResolution(application, List.of(wished.getId()));

        ArgumentCaptor<MatchResolution> saved = ArgumentCaptor.forClass(MatchResolution.class);
        then(matchResolutionRepository).should().save(saved.capture());
        assertThat(saved.getValue().getOfferedSessionIds()).containsExactly(alternative.getId());
        assertThat(saved.getValue().getRespondBy()).isAfter(LocalDateTime.now().plusHours(47));
        assertThat(saved.getValue().getStatus()).isEqualTo(ResolutionStatus.OFFERED);
        assertThat(application.getMatchStatus()).isEqualTo(MatchStatus.ALTERNATIVE_OFFERED);
        assertThat(publishedTypes(1)).containsExactly(NotificationType.DINING_ALTERNATIVE_OFFERED);
        // 매칭 실패: 멤버십으로 확정한 신청이면 기간을 7일 연장한다 (KAN-401)
        then(ticketService).should().extendMembershipForFailure(application.getId());
    }

    @Test
    @DisplayName("제안: 옮길 회차가 없으면 NO_MATCH + 빈 제안")
    void offer_noAlternative_noMatch() {
        givenSessions();

        matchResolutionService.offerResolution(application, List.of(wished.getId(), alternative.getId()));

        ArgumentCaptor<MatchResolution> saved = ArgumentCaptor.forClass(MatchResolution.class);
        then(matchResolutionRepository).should().save(saved.capture());
        assertThat(saved.getValue().getOfferedSessionIds()).isEmpty();
        assertThat(application.getMatchStatus()).isEqualTo(MatchStatus.NO_MATCH);
    }

    // ── 선택 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TRANSFER: 고른 회차로 희망 회차를 교체하고 RESOLVED + DINING_TRANSFERRED, 이용권은 건드리지 않는다")
    void choose_transfer() {
        MatchResolution resolution = offered(List.of(alternative.getId()));
        givenSessions();

        matchResolutionService.chooseResolution(resolution.getId(), userId,
                new ResolutionChooseRequest(ResolutionChoice.TRANSFER, alternative.getId()));

        then(adminApplicationService).should().transferApplication(application, alternative);
        assertThat(resolution.getStatus()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(resolution.getChoice()).isEqualTo(ResolutionChoice.TRANSFER);
        assertThat(resolution.getChosenSessionId()).isEqualTo(alternative.getId());
        assertThat(publishedTypes(1)).containsExactly(NotificationType.DINING_TRANSFERRED);
        then(ticketService).shouldHaveNoInteractions();
        // 보증금은 신청을 따라 새 회차로 간다(반환하지 않음). (KAN-402)
        then(depositService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("TRANSFER: sessionId 누락은 400, 제안 밖 회차는 400")
    void choose_transfer_invalidSession() {
        MatchResolution resolution = offered(List.of(alternative.getId()));

        assertThatThrownBy(() -> matchResolutionService.chooseResolution(resolution.getId(), userId,
                new ResolutionChooseRequest(ResolutionChoice.TRANSFER, null)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOLUTION_SESSION_REQUIRED);

        givenSessions();
        assertThatThrownBy(() -> matchResolutionService.chooseResolution(resolution.getId(), userId,
                new ResolutionChooseRequest(ResolutionChoice.TRANSFER, wished.getId())))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOLUTION_SESSION_NOT_OFFERED);
        assertThat(resolution.getStatus()).isEqualTo(ResolutionStatus.OFFERED);
    }

    @Test
    @DisplayName("KEEP_TICKET: 이용권 복원 + 신청 취소")
    void choose_keepTicket() {
        MatchResolution resolution = offered(List.of());

        matchResolutionService.chooseResolution(resolution.getId(), userId,
                new ResolutionChooseRequest(ResolutionChoice.KEEP_TICKET, null));

        then(ticketService).should().refundOneTicket(application);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(resolution.getStatus()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(resolution.getChoice()).isEqualTo(ResolutionChoice.KEEP_TICKET);
        then(depositService).should().requestReturn(application.getId());
    }

    @Test
    @DisplayName("REFUND: 계좌가 없으면 REFUND_ACCOUNT_REQUIRED, 아무것도 처리하지 않는다")
    void chooseRefund_withoutAccount_throws() {
        MatchResolution resolution = offered(List.of());

        assertThatThrownBy(() -> matchResolutionService.chooseResolution(resolution.getId(), userId,
                new ResolutionChooseRequest(ResolutionChoice.REFUND, null)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_ACCOUNT_REQUIRED);
        then(ticketService).shouldHaveNoInteractions();
        assertThat(resolution.getStatus()).isEqualTo(ResolutionStatus.OFFERED);
    }

    @Test
    @DisplayName("REFUND: 송금 대기(REFUND_PROCESSING) → 신청 취소 + 환불 요청 알림만, 이용권 복원·예외함 없음")
    void choose_refund_processing() {
        MatchResolution resolution = offered(List.of());
        given(ticketService.refundPurchase(application, ACCOUNT)).willReturn(Optional.of(TicketDeductionStatus.REFUND_PROCESSING));

        matchResolutionService.chooseResolution(resolution.getId(), userId, refundRequest());

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(resolution.getChoice()).isEqualTo(ResolutionChoice.REFUND);
        assertThat(publishedTypes(1)).containsExactly(NotificationType.DINING_REFUND_REQUESTED);
        then(ticketService).should(never()).refundOneTicket(any());
        then(depositService).should().requestReturn(application.getId());
    }

    @Test
    @DisplayName("REFUND: 데모 회원은 바로 송금 완료(REFUNDED) → 환불 요청·완료 알림")
    void choose_refund_demoCompleted() {
        MatchResolution resolution = offered(List.of());
        given(ticketService.refundPurchase(application, ACCOUNT)).willReturn(Optional.of(TicketDeductionStatus.REFUNDED));

        matchResolutionService.chooseResolution(resolution.getId(), userId, refundRequest());

        assertThat(publishedTypes(2)).containsExactly(
                NotificationType.DINING_REFUND_REQUESTED, NotificationType.DINING_REFUND_COMPLETED);
    }

    @Test
    @DisplayName("REFUND: 구매 건(차감 기록)을 특정할 수 없으면 이용권 보관과 같이 처리")
    void choose_refund_noPurchase_keepsTicket() {
        MatchResolution resolution = offered(List.of());
        given(ticketService.refundPurchase(application, ACCOUNT)).willReturn(Optional.empty());

        matchResolutionService.chooseResolution(resolution.getId(), userId, refundRequest());

        then(ticketService).should().refundOneTicket(application);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        then(eventPublisher).shouldHaveNoInteractions();
        then(depositService).should().requestReturn(application.getId());
    }

    private static final RefundAccount ACCOUNT = new RefundAccount("국민", "12345678901", "회원");

    private static ResolutionChooseRequest refundRequest() {
        return new ResolutionChooseRequest(ResolutionChoice.REFUND, null, ACCOUNT);
    }

    @Test
    @DisplayName("본인이 아니면 403, 이미 처리된 제안은 409")
    void choose_forbiddenAndConflict() {
        MatchResolution resolution = offered(List.of());
        ResolutionChooseRequest keep = new ResolutionChooseRequest(ResolutionChoice.KEEP_TICKET, null);

        assertThatThrownBy(() -> matchResolutionService.chooseResolution(resolution.getId(), UUID.randomUUID(), keep))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_FORBIDDEN);

        resolution.resolve(ResolutionChoice.KEEP_TICKET, null);
        assertThatThrownBy(() -> matchResolutionService.chooseResolution(resolution.getId(), userId, keep))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOLUTION_ALREADY_HANDLED);
        then(ticketService).shouldHaveNoInteractions();
    }

    // ── 만료 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("만료: 기한이 지난 OFFERED는 EXPIRED + 이용권 보관과 같이 처리")
    void expire_keepsTicket() {
        MatchResolution resolution = MatchResolution.offer(application, List.of(), LocalDateTime.now().minusMinutes(1));
        ReflectionTestUtils.setField(resolution, "id", UUID.randomUUID());
        given(matchResolutionRepository.findByIdForUpdate(resolution.getId())).willReturn(Optional.of(resolution));

        matchResolutionService.expireResolution(resolution.getId());

        assertThat(resolution.getStatus()).isEqualTo(ResolutionStatus.EXPIRED);
        assertThat(resolution.getChoice()).isEqualTo(ResolutionChoice.KEEP_TICKET);
        then(ticketService).should().refundOneTicket(application);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        then(depositService).should().requestReturn(application.getId());
    }

    @Test
    @DisplayName("만료: 잠그는 사이 참가자가 먼저 골랐으면 아무것도 하지 않는다")
    void expire_alreadyResolved_noop() {
        MatchResolution resolution = MatchResolution.offer(application, List.of(), LocalDateTime.now().minusMinutes(1));
        ReflectionTestUtils.setField(resolution, "id", UUID.randomUUID());
        resolution.resolve(ResolutionChoice.REFUND, null);
        given(matchResolutionRepository.findByIdForUpdate(resolution.getId())).willReturn(Optional.of(resolution));

        matchResolutionService.expireResolution(resolution.getId());

        assertThat(resolution.getStatus()).isEqualTo(ResolutionStatus.RESOLVED);
        then(ticketService).shouldHaveNoInteractions();
        then(depositService).shouldHaveNoInteractions();
    }
}
