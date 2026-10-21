package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.application.enums.DiningPaymentStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DiningApplicantResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DiningDashboardResponse;
import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import com.whatsuphouse.backend.domain.dining.repository.SafetyReportRepository;
import com.whatsuphouse.backend.domain.matching.service.MatchResolutionService;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.matching.service.MatchExclusionProvider;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminDiningServiceTest {

    @Mock
    private GatheringService gatheringService;

    @Mock
    private AdminApplicationService adminApplicationService;

    @Mock
    private MatchingService matchingService;

    @Mock
    private DiningAttendanceService diningAttendanceService;

    @Mock
    private ExceptionCaseRepository exceptionCaseRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private MatchExclusionProvider matchExclusionProvider;

    @Mock
    private SafetyReportRepository safetyReportRepository;

    @Mock
    private TicketService ticketService;

    @Mock
    private DepositService depositService;

    @Mock
    private MatchResolutionService matchResolutionService;

    @InjectMocks
    private AdminDiningService adminDiningService;

    private final Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();

    private GatheringSession session(int daysLater) {
        GatheringSession session = GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().plusDays(daysLater)).maxAttendees(12).build();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    private Application application() {
        return application(null);
    }

    private Application application(User user) {
        Application application = Application.builder()
                .bookingNumber("WH261001-" + UUID.randomUUID().toString().substring(0, 6))
                .gathering(gathering).user(user).name("홍길동").phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        return application;
    }

    @Test
    @DisplayName("대시보드 타일은 여러 회차를 희망한 신청을 한 번만 세고, 반려 신청은 대기에서 뺀다")
    void getDashboard_countsDistinctApplicants() {
        GatheringSession first = session(3);
        GatheringSession second = session(10);
        Application shared = application(); // 두 회차 모두 희망, 결제 완료
        shared.confirm();
        Application unpaid = application(); // 결제 대기
        unpaid.awaitPayment();
        Application rejected = application(); // 반려
        rejected.reject("정원 초과");

        given(gatheringService.listUpcomingSessions(GatheringType.RANDOM_TABLE)).willReturn(List.of(first, second));
        given(adminApplicationService.listSessionApplications(first.getId())).willReturn(List.of(shared, unpaid));
        given(adminApplicationService.listSessionApplications(second.getId())).willReturn(List.of(shared, rejected));
        given(matchingService.countTablesBySessionIds(List.of(first.getId(), second.getId())))
                .willReturn(Map.of(first.getId(), Map.of(DiningTableStatus.PROPOSED, 2L, DiningTableStatus.CONFIRMED, 1L)));
        given(matchingService.findSessionIdsWithMatchRun(List.of(first.getId(), second.getId())))
                .willReturn(Set.of(first.getId()));
        given(exceptionCaseRepository.countBySessionIdsAndStatus(any(), eq(ExceptionCaseStatus.OPEN)))
                .willReturn(List.of(new ExceptionCaseRepository.SessionCountProjection() {
                    public UUID getSessionId() { return second.getId(); }
                    public Long getCount() { return 3L; }
                }));
        given(exceptionCaseRepository.countByStatus(ExceptionCaseStatus.OPEN)).willReturn(5L);

        DiningDashboardResponse dashboard = adminDiningService.getDashboard();

        assertThat(dashboard.getUpcomingSessionCount()).isEqualTo(2);
        assertThat(dashboard.getWaitingApplicantCount()).isEqualTo(2);   // shared + unpaid
        assertThat(dashboard.getPaidApplicantCount()).isEqualTo(1);      // shared
        assertThat(dashboard.getProposedTableCount()).isEqualTo(2);
        assertThat(dashboard.getConfirmedTableCount()).isEqualTo(1);
        assertThat(dashboard.getOpenExceptionCount()).isEqualTo(5);

        DiningDashboardResponse.SessionCard firstCard = dashboard.getSessions().get(0);
        assertThat(firstCard.getPaidCount()).isEqualTo(1);
        assertThat(firstCard.getMaxAttendees()).isEqualTo(12);
        assertThat(firstCard.getTableCount()).isEqualTo(3);
        assertThat(firstCard.getIsMatchRunDone()).isTrue();
        assertThat(firstCard.getOpenExceptionCount()).isZero();

        DiningDashboardResponse.SessionCard secondCard = dashboard.getSessions().get(1);
        assertThat(secondCard.getPaidCount()).isEqualTo(1);
        assertThat(secondCard.getTableCount()).isZero();
        assertThat(secondCard.getIsMatchRunDone()).isFalse();
        assertThat(secondCard.getOpenExceptionCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("숨김 종류(심사용) 회차는 타일 집계에서 빠지고, 카드에는 hidden=true로 나온다 (KAN-407, KAN-416)")
    void getDashboard_hiddenKindSession_excludedFromTilesButCarded() {
        GatheringSession visible = session(3);
        Gathering hiddenKind = Gathering.builder().title("심사용").gatheringType(GatheringType.RANDOM_TABLE).build();
        ReflectionTestUtils.setField(hiddenKind, "hidden", true);
        GatheringSession hidden = GatheringSession.builder()
                .gathering(hiddenKind).eventDate(LocalDate.now().plusDays(4)).maxAttendees(12).build();
        ReflectionTestUtils.setField(hidden, "id", UUID.randomUUID());
        // 숨김 회차의 결제 완료 데모 신청: 카드에는 세고 타일에는 넣지 않는다
        User demo = member(28, null);
        demo.markDemo();
        Application demoPaid = application(demo);
        demoPaid.confirm();
        List<UUID> ids = List.of(visible.getId(), hidden.getId());

        given(gatheringService.listUpcomingSessions(GatheringType.RANDOM_TABLE)).willReturn(List.of(visible, hidden));
        given(adminApplicationService.listSessionApplications(visible.getId())).willReturn(List.of());
        given(adminApplicationService.listSessionApplications(hidden.getId())).willReturn(List.of(demoPaid));
        given(matchingService.countTablesBySessionIds(ids)).willReturn(Map.of(
                visible.getId(), Map.of(DiningTableStatus.PROPOSED, 1L),
                hidden.getId(), Map.of(DiningTableStatus.PROPOSED, 4L, DiningTableStatus.CONFIRMED, 2L)));
        given(matchingService.findSessionIdsWithMatchRun(ids)).willReturn(Set.of(hidden.getId()));
        given(exceptionCaseRepository.countBySessionIdsAndStatus(any(), eq(ExceptionCaseStatus.OPEN)))
                .willReturn(List.of(new ExceptionCaseRepository.SessionCountProjection() {
                    public UUID getSessionId() { return hidden.getId(); }
                    public Long getCount() { return 2L; }
                }));
        // 전체 미해결 7건 중 2건이 숨김 종류 회차의 건
        given(exceptionCaseRepository.countByStatus(ExceptionCaseStatus.OPEN)).willReturn(7L);
        given(exceptionCaseRepository.countByStatusOnHiddenGatherings(ExceptionCaseStatus.OPEN)).willReturn(2L);

        DiningDashboardResponse dashboard = adminDiningService.getDashboard();

        // 타일: 숨김 회차가 없을 때와 같다
        assertThat(dashboard.getUpcomingSessionCount()).isEqualTo(1);
        assertThat(dashboard.getWaitingApplicantCount()).isZero();
        assertThat(dashboard.getPaidApplicantCount()).isZero();
        assertThat(dashboard.getProposedTableCount()).isEqualTo(1);
        assertThat(dashboard.getConfirmedTableCount()).isZero();
        assertThat(dashboard.getOpenExceptionCount()).isEqualTo(5);
        // 카드: 숨김 회차도 나오고 hidden=true, 자기 회차 수치를 그대로 보여 준다
        assertThat(dashboard.getSessions()).extracting(DiningDashboardResponse.SessionCard::getSessionId)
                .containsExactly(visible.getId(), hidden.getId());
        assertThat(dashboard.getSessions().get(0).isHidden()).isFalse();
        DiningDashboardResponse.SessionCard hiddenCard = dashboard.getSessions().get(1);
        assertThat(hiddenCard.isHidden()).isTrue();
        assertThat(hiddenCard.getPaidCount()).isEqualTo(1);
        assertThat(hiddenCard.getTableCount()).isEqualTo(6);
        assertThat(hiddenCard.getIsMatchRunDone()).isTrue();
        assertThat(hiddenCard.getOpenExceptionCount()).isEqualTo(2);
    }

    private User member(Integer age, LocalDate birthDate) {
        User user = User.builder().email(UUID.randomUUID() + "@test.com").name("회원").age(age).birthDate(birthDate).build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private ApplicationAnswer answer(Application application, String questionKey, ReservedQuestionKey reservedKey, Object value) {
        FormQuestion question = FormQuestion.builder()
                .questionKey(questionKey).type(QuestionType.NUMBER).label(questionKey).reservedKey(reservedKey).build();
        return ApplicationAnswer.builder().application(application).question(question).value(Map.of("value", value)).build();
    }

    @Test
    @DisplayName("신청자 표 나이는 출생연도(BIRTH_YEAR) 답으로 계산하고, 없거나 잘못된 값이면 회원 나이로 대신한다")
    void listApplicants_ageFromBirthYear() {
        GatheringSession session = session(3);
        int thisYear = Year.now().getValue();
        Application fromYear = application(member(20, null));                              // 출생연도 1995 → 올해 - 1995
        User sameYearMember = member(null, LocalDate.of(1995, 12, 31));
        Application sameYear = application(sameYearMember);                                // 회원 생년월일과 같은 연도 → 회원 만 나이
        Application noAnswer = application(member(33, null));                              // 출생연도 답 없음(옛 '나이' 답만) → 회원 나이
        Application badAnswer = application(member(41, null));                             // 연도로 못 읽는 값 → 회원 나이

        given(adminApplicationService.listSessionApplications(session.getId()))
                .willReturn(List.of(fromYear, sameYear, noAnswer, badAnswer));
        given(adminApplicationService.findAnswers(List.of(fromYear.getId(), sameYear.getId(), noAnswer.getId(), badAnswer.getId())))
                .willReturn(List.of(
                        answer(fromYear, "birth_year", ReservedQuestionKey.BIRTH_YEAR, 1995),
                        answer(sameYear, "birth_year", ReservedQuestionKey.BIRTH_YEAR, "1995"),
                        answer(noAnswer, "age", null, 25),
                        answer(badAnswer, "birth_year", ReservedQuestionKey.BIRTH_YEAR, "95년생")));
        // 참가 횟수는 참석(ATTENDED) 집계에서 읽고, 집계에 없는 회원은 0. (KAN-392)
        given(diningAttendanceService.countAttendedByUserIds(any()))
                .willReturn(Map.of(fromYear.getUser().getId(), 2L));

        List<DiningApplicantResponse> applicants = adminDiningService.listApplicants(session.getId());

        assertThat(applicants).extracting(DiningApplicantResponse::getAge).containsExactly(
                String.valueOf(thisYear - 1995),
                String.valueOf(sameYearMember.getCurrentAge()),
                "33",
                "41");
        assertThat(applicants).extracting(DiningApplicantResponse::getParticipationCount).containsExactly(2L, 0L, 0L, 0L);
    }

    // ── 운영 지표·신청자 필터 (KAN-437) ───────────────────────────────────────

    private Application confirmed(MatchStatus matchStatus, DiningRegion... regions) {
        Application application = Application.builder()
                .bookingNumber("WH261021-" + UUID.randomUUID().toString().substring(0, 6))
                .gathering(gathering).user(member(30, null)).name("회원").phone("01012345678")
                .candidateRegions(List.of(regions)).build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.confirm();
        application.changeMatchStatus(matchStatus);
        return application;
    }

    private static ExceptionCaseRepository.TypeCountProjection typeCount(ExceptionCaseType type, long count) {
        return new ExceptionCaseRepository.TypeCountProjection() {
            public ExceptionCaseType getType() { return type; }
            public Long getCount() { return count; }
        };
    }

    @Test
    @DisplayName("운영 지표: 신청자·매칭·확정·재배치·성공률·해결 대기는 회차 신청에서, 문제 지표는 예외 유형별 열린 건·테이블·참석·환불 집계에서")
    void getDashboard_operationMetrics() {
        // given
        GatheringSession session = session(3);
        Application seated = confirmed(MatchStatus.CONFIRM_PENDING);
        Application confirmedA = confirmed(MatchStatus.CONFIRMED);
        Application confirmedB = confirmed(MatchStatus.CONFIRMED);
        Application reallocating = confirmed(MatchStatus.REALLOCATING);
        Application offered = confirmed(MatchStatus.ALTERNATIVE_OFFERED);
        Application rejected = application();
        rejected.reject("자격 미달");
        List<UUID> sessionIds = List.of(session.getId());
        given(gatheringService.listUpcomingSessions(GatheringType.RANDOM_TABLE)).willReturn(List.of(session));
        given(adminApplicationService.listSessionApplications(session.getId()))
                .willReturn(List.of(seated, confirmedA, confirmedB, reallocating, offered, rejected));
        given(matchingService.countMatchRuns(sessionIds)).willReturn(2L);
        given(matchingService.countConfirmedTablesWithoutVenue(sessionIds)).willReturn(1L);
        given(diningAttendanceService.countNoShowCandidates(sessionIds)).willReturn(3L);
        given(matchResolutionService.findOfferedResolutionIds(anyCollection()))
                .willReturn(Map.of(offered.getId(), UUID.randomUUID()));
        given(ticketService.countRefundFailures()).willReturn(1L);
        given(exceptionCaseRepository.countByStatusGroupByTypeExcludingHidden(ExceptionCaseStatus.OPEN)).willReturn(List.of(
                typeCount(ExceptionCaseType.CONFLICT, 2),
                typeCount(ExceptionCaseType.NOTIFICATION, 4),
                typeCount(ExceptionCaseType.REFUND, 1),
                typeCount(ExceptionCaseType.SAFETY, 5)));

        // when
        DiningDashboardResponse dashboard = adminDiningService.getDashboard();

        // then
        assertThat(dashboard.getTotalApplicantCount()).isEqualTo(5);          // 반려 제외
        assertThat(dashboard.getMatchRunCount()).isEqualTo(2);
        assertThat(dashboard.getMatchedApplicantCount()).isEqualTo(3);        // CONFIRM_PENDING + CONFIRMED 2
        assertThat(dashboard.getReallocatedApplicantCount()).isEqualTo(1);
        assertThat(dashboard.getConfirmedApplicantCount()).isEqualTo(2);
        assertThat(dashboard.getMatchSuccessRate()).isEqualTo(2.0 / 5);       // 결제 완료 5명 중 확정 2명
        assertThat(dashboard.getResolutionPendingCount()).isEqualTo(1);
        assertThat(dashboard.getConflictCount()).isEqualTo(2);
        assertThat(dashboard.getNoVenueTableCount()).isEqualTo(1);
        assertThat(dashboard.getNotificationFailureCount()).isEqualTo(4);
        assertThat(dashboard.getRefundFailureCount()).isEqualTo(2);           // REFUND 예외 1 + 환불 실패 차감 1
        assertThat(dashboard.getNoShowCandidateCount()).isEqualTo(3);
        assertThat(dashboard.getOpenReportCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("결제 완료 신청자가 없으면 매칭 성공률은 0")
    void getDashboard_noPaidApplicant_zeroSuccessRate() {
        // given
        given(gatheringService.listUpcomingSessions(GatheringType.RANDOM_TABLE)).willReturn(List.of());

        // when
        DiningDashboardResponse dashboard = adminDiningService.getDashboard();

        // then
        assertThat(dashboard.getMatchSuccessRate()).isZero();
        assertThat(dashboard.getTotalApplicantCount()).isZero();
    }

    @Test
    @DisplayName("신청자 표: 통합 결제 상태·신고된 수를 붙이고 paymentStatus·matchStatus·region으로 거른다")
    void listApplicants_filtersAndPaymentStatus() {
        // given
        GatheringSession session = session(3);
        Application paidGangnam = confirmed(MatchStatus.WAITING, DiningRegion.GANGNAM);
        Application paidHongdae = confirmed(MatchStatus.CONFIRMED, DiningRegion.HONGDAE);
        Application offered = confirmed(MatchStatus.ALTERNATIVE_OFFERED, DiningRegion.GANGNAM, DiningRegion.JAMSIL);
        Application unpaid = application(member(30, null));
        unpaid.awaitPayment();
        given(adminApplicationService.listSessionApplications(session.getId()))
                .willReturn(List.of(paidGangnam, paidHongdae, offered, unpaid));
        given(ticketService.findDeductionStatuses(anyCollection())).willReturn(Map.of(
                paidGangnam.getId(), TicketDeductionStatus.DEDUCTED,
                paidHongdae.getId(), TicketDeductionStatus.DEDUCTED,
                offered.getId(), TicketDeductionStatus.DEDUCTED));
        given(matchResolutionService.findOfferedResolutionIds(anyCollection()))
                .willAnswer(invocation -> invocation.<java.util.Collection<UUID>>getArgument(0).contains(offered.getId())
                        ? Map.of(offered.getId(), UUID.randomUUID()) : Map.of());
        UUID reportedUserId = offered.getUser().getId();
        given(safetyReportRepository.countByReportedUserIds(anyCollection()))
                .willReturn(List.of(new SafetyReportRepository.UserCountProjection() {
                    public UUID getUserId() { return reportedUserId; }
                    public Long getCount() { return 2L; }
                }));

        // when
        List<DiningApplicantResponse> all = adminDiningService.listApplicants(session.getId(), null, null, null);
        List<DiningApplicantResponse> paid = adminDiningService.listApplicants(session.getId(), DiningPaymentStatus.PAID, null, null);
        List<DiningApplicantResponse> gangnamWaiting = adminDiningService.listApplicants(
                session.getId(), null, MatchStatus.WAITING, "강남/신논현");
        List<DiningApplicantResponse> jamsil = adminDiningService.listApplicants(session.getId(), null, null, "잠실");

        // then
        assertThat(all).extracting(DiningApplicantResponse::getApplicationId, DiningApplicantResponse::getPaymentStatus,
                        DiningApplicantResponse::getReportCount)
                .containsExactly(
                        tuple(paidGangnam.getId(), DiningPaymentStatus.PAID, 0L),
                        tuple(paidHongdae.getId(), DiningPaymentStatus.PAID, 0L),
                        tuple(offered.getId(), DiningPaymentStatus.REFUND_AVAILABLE, 2L),
                        tuple(unpaid.getId(), DiningPaymentStatus.PENDING, 0L));
        assertThat(paid).extracting(DiningApplicantResponse::getApplicationId)
                .containsExactly(paidGangnam.getId(), paidHongdae.getId());
        assertThat(gangnamWaiting).extracting(DiningApplicantResponse::getApplicationId).containsExactly(paidGangnam.getId());
        assertThat(jamsil).extracting(DiningApplicantResponse::getApplicationId).containsExactly(offered.getId());
    }

    @Test
    @DisplayName("모르는 지역 라벨로 거르면 CustomException(INVALID_REGION)")
    void listApplicants_unknownRegion_throwsCustomException() {
        // when & then
        assertThatThrownBy(() -> adminDiningService.listApplicants(UUID.randomUUID(), null, null, "부산"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REGION);
    }
}
