package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DiningApplicantResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DiningDashboardResponse;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
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
import static org.mockito.ArgumentMatchers.any;
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
}
