package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.dining.admin.service.AdminMatchingRuleService;
import com.whatsuphouse.backend.domain.dining.entity.MatchingRuleSetting;
import com.whatsuphouse.backend.domain.dining.service.SessionVenueService;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchRunResponse;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.entity.MatchRun;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.enums.MatchRunTrigger;
import com.whatsuphouse.backend.domain.matching.enums.UnassignedReason;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.notification.event.DiningReallocatingEvent;
import com.whatsuphouse.backend.domain.matching.repository.MatchRunRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DiningMatchServiceTest {

    @Mock
    private GatheringService gatheringService;
    @Mock
    private AdminApplicationService adminApplicationService;
    @Mock
    private AdminMatchingRuleService adminMatchingRuleService;
    @Mock
    private SessionVenueService sessionVenueService;
    @Mock
    private MatchExclusionProvider matchExclusionProvider;
    @Spy
    private MatchingEngine matchingEngine = new MatchingEngine();
    @Mock
    private DiningTableRepository diningTableRepository;
    @Mock
    private DiningTableMemberRepository diningTableMemberRepository;
    @Mock
    private MatchRunRepository matchRunRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MatchResolutionService matchResolutionService;

    @InjectMocks
    private DiningMatchService diningMatchService;

    private final Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
    private GatheringSession session;
    private GatheringSession nextSession;
    private List<Application> applications;

    private GatheringSession session(int daysLater) {
        GatheringSession s = GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().plusDays(daysLater)).maxAttendees(12).build();
        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
        return s;
    }

    // 5명, 4인 고정 테이블 → 4명 착석 + 입력 순서가 가장 늦은 4번 1명 미배정. 답변이 없어 점수는 0이라 최소 점수 0.
    @BeforeEach
    void setUp() {
        session = session(7);
        session.changeMatchingRules(null, 30, 4, 4, BigDecimal.ZERO, null);
        nextSession = session(14);
        applications = IntStream.range(0, 5).mapToObj(i -> {
            User user = User.builder().email("u" + i + "@example.com").password("pw").name("회원" + i)
                    .gender(Gender.MALE).age(30).nickname("회원" + i).build();
            ReflectionTestUtils.setField(user, "id", new UUID(1, i));
            user.approveRandomTable();
            Application application = Application.builder().bookingNumber("WH-" + i).gathering(gathering).user(user)
                    .name("회원" + i).phone("01012345678").build();
            ReflectionTestUtils.setField(application, "id", new UUID(0, i));
            application.confirm();
            return application;
        }).toList();

        given(gatheringService.lockRandomTableSession(session.getId())).willReturn(session);
    }

    // 실행 1회에 필요한 조회 스텁. lastHasNextSession이면 4번 신청은 아직 실행 전인 다른 회차도 희망한다.
    private void givenRun(boolean lastHasNextSession) {
        given(adminMatchingRuleService.findMatchingRuleSetting()).willReturn(MatchingRuleSetting.defaults());
        given(matchRunRepository.save(any(MatchRun.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(adminApplicationService.listSessionApplications(session.getId())).willReturn(applications);
        given(diningTableMemberRepository.findApplicationIdsByTableStatusIn(anyCollection(), anyCollection()))
                .willReturn(List.of());
        given(adminApplicationService.findAnswers(anyCollection())).willReturn(List.of());
        given(diningTableMemberRepository.findUserPairsByTableStatusIn(anyCollection(), anyCollection(), isNull()))
                .willReturn(List.of());
        given(matchExclusionProvider.findExcludedPairs(anyCollection())).willReturn(Map.of());
        wishes(lastHasNextSession);
    }

    private void wishes(boolean lastHasNextSession) {
        Map<UUID, List<ApplicationCandidateSession>> wishes = new HashMap<>();
        for (Application application : applications) {
            List<ApplicationCandidateSession> list = new ArrayList<>(List.of(new ApplicationCandidateSession(application, session, 1)));
            if (lastHasNextSession && application == applications.get(4)) {
                list.add(new ApplicationCandidateSession(application, nextSession, 2));
            }
            wishes.put(application.getId(), list);
        }
        given(adminApplicationService.findCandidateSessions(anyCollection())).willReturn(wishes);
    }

    @Test
    @DisplayName("마지막 희망 회차에서 못 앉으면 ALTERNATIVE_OFFERED + 해결 선택 제안, 앉은 사람은 CONFIRM_PENDING + PROPOSED 테이블")
    void runMatch_lastWish_alternativeOffered() {
        givenRun(false);
        LocalDateTime before = LocalDateTime.now();

        MatchRunResponse response = diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, null);

        assertThat(response.getCandidateCount()).isEqualTo(5);
        assertThat(response.getTableCount()).isEqualTo(1);
        assertThat(response.getUnassignedCount()).isEqualTo(1);
        assertThat(applications.subList(0, 4)).allSatisfy(a -> assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.CONFIRM_PENDING));
        assertThat(applications.get(4).getMatchStatus()).isEqualTo(MatchStatus.ALTERNATIVE_OFFERED);

        ArgumentCaptor<DiningTable> table = ArgumentCaptor.forClass(DiningTable.class);
        then(diningTableRepository).should().save(table.capture());
        assertThat(table.getValue().getStatus()).isEqualTo(DiningTableStatus.PROPOSED);
        assertThat(table.getValue().getConfirmAt()).isAfterOrEqualTo(before.plusMinutes(30));
        then(diningTableMemberRepository).should(times(4)).save(any(DiningTableMember.class));

        ArgumentCaptor<MatchRun> run = ArgumentCaptor.forClass(MatchRun.class);
        then(matchRunRepository).should().save(run.capture());
        assertThat(run.getValue().getUnassignedReasons())
                .containsExactly(new MatchRun.Unassigned(new UUID(0, 4), UnassignedReason.NOT_ENOUGH_PEOPLE));
        then(matchResolutionService).should().offerResolution(applications.get(4), List.of(session.getId()));
    }

    @Test
    @DisplayName("아직 실행 전인 다른 희망 회차가 있으면 REALLOCATING + NEXT_SESSION_WAITING")
    void runMatch_nextWishPending_reallocating() {
        givenRun(true);
        given(matchRunRepository.findSessionIdsIn(anyCollection())).willReturn(List.of());

        diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, null);

        assertThat(applications.get(4).getMatchStatus()).isEqualTo(MatchStatus.REALLOCATING);
        ArgumentCaptor<DiningReallocatingEvent> event = ArgumentCaptor.forClass(DiningReallocatingEvent.class);
        then(eventPublisher).should().publishEvent(event.capture());
        assertThat(event.getValue().getApplicationId()).isEqualTo(new UUID(0, 4));
        ArgumentCaptor<MatchRun> run = ArgumentCaptor.forClass(MatchRun.class);
        then(matchRunRepository).should().save(run.capture());
        assertThat(run.getValue().getUnassignedReasons())
                .containsExactly(new MatchRun.Unassigned(new UUID(0, 4), UnassignedReason.NEXT_SESSION_WAITING));
    }

    @Test
    @DisplayName("재실행은 잠기지 않은 제안 테이블을 해체하고 식당 사용 수를 돌려놓으며, 그 멤버를 다시 후보로 쓴다")
    void runMatch_rerun_dissolvesUnlockedProposedTables() {
        givenRun(false);
        Application previousMember = applications.get(0);
        previousMember.changeMatchStatus(MatchStatus.CONFIRM_PENDING);
        UUID venueId = UUID.randomUUID();
        DiningTable previous = DiningTable.builder().session(session).eventDate(session.getEventDate()).groupSize(1)
                .algorithmVersion("rule-v2").build();
        ReflectionTestUtils.setField(previous, "id", UUID.randomUUID());
        previous.assignVenue(venueId);
        given(diningTableRepository.findBySession_IdAndStatusAndLockedFalseAndDeletedAtIsNull(
                session.getId(), DiningTableStatus.PROPOSED)).willReturn(List.of(previous));
        given(diningTableMemberRepository.findByTableIdsWithApplication(List.of(previous.getId())))
                .willReturn(List.of(DiningTableMember.builder().application(previousMember).table(previous)
                        .assignReason(AssignReason.INITIAL).build()));

        MatchRunResponse response = diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, null);

        assertThat(previous.getStatus()).isEqualTo(DiningTableStatus.DISSOLVED);
        assertThat(previous.getVenueId()).isNull();
        then(sessionVenueService).should().releaseTable(session.getId(), venueId);
        assertThat(response.getCandidateCount()).isEqualTo(5);
        assertThat(previousMember.getMatchStatus()).isEqualTo(MatchStatus.CONFIRM_PENDING);
    }

    @Test
    @DisplayName("취소된 회차는 409")
    void runMatch_cancelledSession_conflict() {
        session.changeStatus(GatheringSessionStatus.CANCELLED);

        assertThatThrownBy(() -> diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_MATCHABLE);
    }
}
