package com.whatsuphouse.backend.domain.application.service;

import com.whatsuphouse.backend.domain.application.client.dto.request.DiningWishUpdateRequest;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.service.DiningApplicationService;
import com.whatsuphouse.backend.domain.application.client.service.MembershipWeekRule;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import com.whatsuphouse.backend.domain.form.client.service.FormService;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringSessionService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.enums.UnassignedReason;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.matching.service.DiningTableService;
import com.whatsuphouse.backend.domain.matching.service.MatchResolutionService;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProductKind;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/** 매칭 대기 중 일정·지역 바꾸기(STA-06)와 내 신청 목록의 매칭 시각·미배정 사유(STA-02·MAT-12). (KAN-436) */
@ExtendWith(MockitoExtension.class)
class DiningApplicationWishTest {

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private ApplicationCandidateSessionRepository applicationCandidateSessionRepository;
    @Mock
    private ApplicationAnswerRepository applicationAnswerRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private MatchingService matchingService;
    @Mock
    private DiningAttendanceService diningAttendanceService;
    @Mock
    private DiningTableService diningTableService;
    @Mock
    private MatchResolutionService matchResolutionService;
    @Mock
    private FormService formService;
    @Mock
    private DepositService depositService;
    @Mock
    private GatheringSessionService gatheringSessionService;
    @Mock
    private MembershipWeekRule membershipWeekRule;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DiningApplicationService diningApplicationService;

    private final UUID userId = UUID.randomUUID();
    private Gathering randomTable;
    private Application application;
    private GatheringSession current;
    private GatheringSession next;
    private GatheringSession later;

    @BeforeEach
    void setUp() {
        randomTable = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        ReflectionTestUtils.setField(randomTable, "id", UUID.randomUUID());
        User user = User.builder().email("u@example.com").password("pw").name("회원").gender(Gender.MALE).age(30)
                .nickname("회원").phone("01012345678").build();
        ReflectionTestUtils.setField(user, "id", userId);
        application = Application.builder().bookingNumber("WH-1").gathering(randomTable).user(user)
                .name("회원").phone("01012345678").candidateRegions(List.of(DiningRegion.HONGDAE)).build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.confirm();
        current = session(3);
        next = session(10);
        later = session(17);
    }

    private GatheringSession session(int daysLater) {
        GatheringSession s = GatheringSession.builder().gathering(randomTable)
                .eventDate(LocalDate.now().plusDays(daysLater)).maxAttendees(12).build();
        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
        return s;
    }

    private void givenApplicationWithCurrentWish() {
        given(applicationRepository.findByIdAndDeletedAtIsNull(application.getId())).willReturn(Optional.of(application));
        given(applicationCandidateSessionRepository.findByApplicationIdsWithSession(List.of(application.getId())))
                .willReturn(List.of(new ApplicationCandidateSession(application, current, 1)));
        for (GatheringSession s : List.of(current, next, later)) {
            given(gatheringSessionService.findSessionForUpdate(s.getId())).willReturn(Optional.of(s));
        }
    }

    private DiningWishUpdateRequest request(List<String> regions) {
        return new DiningWishUpdateRequest(List.of(later.getId(), next.getId()), regions);
    }

    // ── updateWishes() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("WAITING·REALLOCATING이면 희망 회차를 요청 순(우선순위)으로, 희망 지역을 지망 순으로 통째로 바꾼다. 회차를 잠근 뒤 신청을 잠근다")
    void updateWishes_waitingOrReallocating_replacesWishes() {
        givenApplicationWithCurrentWish();
        given(matchingService.findSessionIdsWithMatchRun(List.of(later.getId(), next.getId()))).willReturn(Set.of());
        for (MatchStatus status : List.of(MatchStatus.WAITING, MatchStatus.REALLOCATING)) {
            // given
            clearInvocations(gatheringSessionService, applicationRepository, applicationCandidateSessionRepository);
            application.changeMatchStatus(status);
            application.changeCandidateRegions(List.of(DiningRegion.HONGDAE));

            // when
            diningApplicationService.updateWishes(application.getId(), userId, request(List.of("잠실", "홍대/합정")));

            // then
            assertThat(application.getCandidateRegions()).containsExactly(DiningRegion.JAMSIL, DiningRegion.HONGDAE);
            assertThat(application.getMatchStatus()).isEqualTo(status);
            InOrder order = inOrder(gatheringSessionService, applicationRepository, applicationCandidateSessionRepository);
            order.verify(gatheringSessionService, times(3)).findSessionForUpdate(any());
            order.verify(applicationRepository).refreshForUpdate(application);
            order.verify(applicationCandidateSessionRepository).deleteByApplication_Id(application.getId());
            ArgumentCaptor<ApplicationCandidateSession> saved = ArgumentCaptor.forClass(ApplicationCandidateSession.class);
            then(applicationCandidateSessionRepository).should(times(2)).save(saved.capture());
            assertThat(saved.getAllValues())
                    .extracting(c -> c.getSession().getId(), ApplicationCandidateSession::getPriority)
                    .containsExactly(tuple(later.getId(), 1), tuple(next.getId(), 2));
        }
        // 결제·이용권·보증금은 건드리지 않는다(멤버십 사용 여부 조회만)
        then(ticketService).should(times(2)).findMembershipUsedBy(application.getId());
        then(ticketService).shouldHaveNoMoreInteractions();
        then(depositService).shouldHaveNoInteractions();
    }

    // 1개월 멤버십(오늘 시작)으로 확정한 신청
    private TicketPass givenMembershipUse() {
        TicketPass membership = new TicketPass(application.getUser(), null,
                new TicketProductOption("멤버십 1개월", 0, 35000, TicketProductKind.PERIOD, 1));
        membership.activate(LocalDateTime.now());
        given(ticketService.findMembershipUsedBy(application.getId())).willReturn(Optional.of(membership));
        return membership;
    }

    @Test
    @DisplayName("멤버십 신청: 마지막 희망 회차가 멤버십 기간 밖이면 MEMBERSHIP_PERIOD_EXCEEDED, 희망 회차를 지우지 않는다")
    void updateWishes_membershipOutsidePeriod_throws() {
        // given: 1개월 멤버십인데 40일 뒤 회차로 바꾸려 한다
        GatheringSession farAway = session(40);
        given(applicationRepository.findByIdAndDeletedAtIsNull(application.getId())).willReturn(Optional.of(application));
        given(gatheringSessionService.findSessionForUpdate(farAway.getId())).willReturn(Optional.of(farAway));
        given(matchingService.findSessionIdsWithMatchRun(List.of(farAway.getId()))).willReturn(Set.of());
        givenMembershipUse();

        // when & then
        assertThatThrownBy(() -> diningApplicationService.updateWishes(application.getId(), userId,
                new DiningWishUpdateRequest(List.of(farAway.getId()), List.of("잠실"))))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBERSHIP_PERIOD_EXCEEDED);
        then(applicationCandidateSessionRepository).should(never()).deleteByApplication_Id(any());
        then(membershipWeekRule).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("멤버십 신청: 새 희망 회차 주에 다른 활성 신청이 있으면 주 1회 규칙 위반 코드(MEMBERSHIP_WEEKLY_LIMIT)")
    void updateWishes_membershipWeeklyLimit_throws() {
        // given
        givenApplicationWithCurrentWish();
        given(matchingService.findSessionIdsWithMatchRun(List.of(later.getId(), next.getId()))).willReturn(Set.of());
        givenMembershipUse();
        given(membershipWeekRule.check(userId, application.getId(), List.of(later.getEventDate(), next.getEventDate())))
                .willReturn(Optional.of(ErrorCode.MEMBERSHIP_WEEKLY_LIMIT));

        // when & then
        assertThatThrownBy(() -> diningApplicationService.updateWishes(application.getId(), userId, request(List.of("잠실"))))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBERSHIP_WEEKLY_LIMIT);
        then(applicationCandidateSessionRepository).should(never()).deleteByApplication_Id(any());
        assertThat(application.getCandidateRegions()).containsExactly(DiningRegion.HONGDAE);
    }

    @Test
    @DisplayName("매칭 대기가 아니면(CONFIRM_PENDING 등) WISH_CHANGE_NOT_ALLOWED(409)이고 아무것도 바꾸지 않는다")
    void updateWishes_notWaiting_throwsConflict() {
        // given
        application.changeMatchStatus(MatchStatus.CONFIRM_PENDING);
        given(applicationRepository.findByIdAndDeletedAtIsNull(application.getId())).willReturn(Optional.of(application));

        // when & then
        assertThatThrownBy(() -> diningApplicationService.updateWishes(application.getId(), userId, request(List.of("잠실"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WISH_CHANGE_NOT_ALLOWED);
        assertThat(application.getCandidateRegions()).containsExactly(DiningRegion.HONGDAE);
        then(applicationCandidateSessionRepository).should(never()).deleteByApplication_Id(any());
    }

    @Test
    @DisplayName("매칭을 이미 실행한 회차로는 바꿀 수 없다(WISH_CHANGE_NOT_ALLOWED), 지역이 비면 REGION_REQUIRED, 남의 신청이면 403")
    void updateWishes_invalidTargets_throw() {
        // given
        givenApplicationWithCurrentWish();
        given(matchingService.findSessionIdsWithMatchRun(anyCollection())).willReturn(Set.of(next.getId()));

        // when & then
        assertThatThrownBy(() -> diningApplicationService.updateWishes(application.getId(), userId, request(List.of("잠실"))))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WISH_CHANGE_NOT_ALLOWED);
        assertThatThrownBy(() -> diningApplicationService.updateWishes(application.getId(), userId, request(List.of())))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGION_REQUIRED);
        assertThatThrownBy(() -> diningApplicationService.updateWishes(application.getId(), UUID.randomUUID(), request(List.of("잠실"))))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_FORBIDDEN);
        then(applicationCandidateSessionRepository).should(never()).deleteByApplication_Id(any());
        assertThat(application.getCandidateRegions()).containsExactly(DiningRegion.HONGDAE);
    }

    @Test
    @DisplayName("신청 마감이 지난 회차로는 바꿀 수 없다(신청과 같은 APPLY_DEADLINE_PASSED)")
    void updateWishes_deadlinePassed_throws() {
        // given
        ReflectionTestUtils.setField(next, "applyDeadlineAt", LocalDateTime.now().minusHours(1));
        givenApplicationWithCurrentWish();

        // when & then
        assertThatThrownBy(() -> diningApplicationService.updateWishes(application.getId(), userId, request(List.of("잠실"))))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLY_DEADLINE_PASSED);
        then(applicationCandidateSessionRepository).should(never()).deleteByApplication_Id(any());
    }

    // ── listMyApplications() ──────────────────────────────────────────────

    @Test
    @DisplayName("내 신청 목록: 희망 회차에 매칭 시각(matchRunAt), 아이템에 마지막 실행의 미배정 사유를 준다")
    void listMyApplications_exposesMatchRunAtAndUnassignedReason() {
        // given
        LocalDateTime matchRunAt = LocalDateTime.now().plusDays(2).withNano(0);
        current.changeMatchingRules(matchRunAt, null, null, null, null, null);
        application.changeMatchStatus(MatchStatus.REALLOCATING);
        given(applicationRepository.findMyApplicationsIncludingRecentlyCancelled(eq(userId), eq(GatheringType.RANDOM_TABLE),
                eq(ApplicationStatus.CANCELLED), any())).willReturn(List.of(application));
        given(applicationCandidateSessionRepository.findByApplicationIdsWithSession(List.of(application.getId())))
                .willReturn(List.of(new ApplicationCandidateSession(application, current, 1),
                        new ApplicationCandidateSession(application, next, 2)));
        given(matchingService.findLastUnassignedReasons(Map.of(application.getId(), List.of(current.getId(), next.getId()))))
                .willReturn(Map.of(application.getId(), UnassignedReason.NEXT_SESSION_WAITING));

        // when
        DiningApplicationListResponse response = diningApplicationService.listMyApplications(userId);

        // then
        DiningApplicationListResponse.Item item = response.getApplications().get(0);
        assertThat(item.getUnassignedReason()).isEqualTo(UnassignedReason.NEXT_SESSION_WAITING);
        assertThat(item.getCandidateSessions()).extracting(DiningApplicationListResponse.SessionInfo::getMatchRunAt)
                .containsExactly(matchRunAt, null);
    }
}
