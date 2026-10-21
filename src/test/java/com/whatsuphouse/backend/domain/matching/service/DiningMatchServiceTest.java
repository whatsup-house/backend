package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.dining.admin.service.AdminMatchingRuleService;
import com.whatsuphouse.backend.domain.dining.entity.MatchingRuleSetting;
import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
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
import com.whatsuphouse.backend.domain.payment.service.DepositService;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private DepositService depositService;

    @InjectMocks
    private DiningMatchService diningMatchService;

    private final Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
    private GatheringSession session;
    private GatheringSession nextSession;
    private List<Application> applications;
    // 회원 ID 기준 같은 테이블 금지 관계(매칭 제외). 기본은 없음.
    private Map<UUID, Set<UUID>> blocked = Map.of();

    private GatheringSession session(int daysLater) {
        GatheringSession s = GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().plusDays(daysLater)).maxAttendees(12).build();
        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
        return s;
    }

    // 5명(모두 홍대/합정 희망), 4인 고정 테이블 → 4명 착석 + 입력 순서가 가장 늦은 4번 1명 미배정. 답변이 없어 점수는 0이라 최소 점수 0.
    @BeforeEach
    void setUp() {
        session = session(7);
        session.changeMatchingRules(null, 30, 4, 4, BigDecimal.ZERO, null);
        nextSession = session(14);
        applications = IntStream.range(0, 5).mapToObj(i -> application(i, DiningRegion.HONGDAE)).toList();

        lenient().when(gatheringService.lockRandomTableSession(session.getId())).thenReturn(session);
    }

    private Application application(int i, DiningRegion... regions) {
        User user = User.builder().email("u" + i + "@example.com").password("pw").name("회원" + i)
                .gender(Gender.MALE).age(30).nickname("회원" + i).build();
        ReflectionTestUtils.setField(user, "id", new UUID(1, i));
        user.approveRandomTable();
        Application application = Application.builder().bookingNumber("WH-" + i).gathering(gathering).user(user)
                .name("회원" + i).phone("01012345678").candidateRegions(List.of(regions)).build();
        ReflectionTestUtils.setField(application, "id", new UUID(0, i));
        application.confirm();
        return application;
    }

    // 실행 1회에 필요한 조회 스텁. lastHasNextSession이면 4번 신청은 아직 실행 전인 다른 회차도 희망한다.
    // 기존 테스트는 테이블 1개짜리라 지역 최소 테이블 수를 1로 둔다(지역 기준과 무관한 동작을 본다).
    private void givenRun(boolean lastHasNextSession) {
        givenRun(1, lastHasNextSession ? List.of(applications.get(4)) : List.of());
    }

    private void givenRun(int minTablesPerRegion, Collection<Application> withNextSession) {
        MatchingRuleSetting setting = MatchingRuleSetting.defaults();
        setting.changeMinTablesPerRegion(minTablesPerRegion);
        given(adminMatchingRuleService.findMatchingRuleSetting()).willReturn(setting);
        given(matchRunRepository.save(any(MatchRun.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(adminApplicationService.listSessionApplications(session.getId())).willAnswer(invocation -> applications);
        given(diningTableMemberRepository.findApplicationIdsByTableStatusIn(anyCollection(), anyCollection()))
                .willReturn(List.of());
        given(adminApplicationService.findAnswers(anyCollection())).willReturn(List.of());
        given(diningTableMemberRepository.findUserPairsByTableStatusIn(anyCollection(), anyCollection(), isNull()))
                .willReturn(List.of());
        given(matchExclusionProvider.findExcludedPairs(anyCollection())).willAnswer(invocation -> blocked);
        wishes(withNextSession);
    }

    private void wishes(Collection<Application> withNextSession) {
        Map<UUID, List<ApplicationCandidateSession>> wishes = new HashMap<>();
        for (Application application : applications) {
            List<ApplicationCandidateSession> list = new ArrayList<>(List.of(new ApplicationCandidateSession(application, session, 1)));
            if (withNextSession.contains(application)) {
                list.add(new ApplicationCandidateSession(application, nextSession, 2));
            }
            wishes.put(application.getId(), list);
        }
        given(adminApplicationService.findCandidateSessions(anyCollection())).willReturn(wishes);
    }

    private List<Application> applications(int from, int to, DiningRegion... regions) {
        return IntStream.range(from, to).mapToObj(i -> application(i, regions)).toList();
    }

    private MatchRun savedRun() {
        ArgumentCaptor<MatchRun> run = ArgumentCaptor.forClass(MatchRun.class);
        then(matchRunRepository).should().save(run.capture());
        return run.getValue();
    }

    // ── 회차×지역 매칭 (KAN-431) ─────────────────────────────────────────────

    @Test
    @DisplayName("홍대 8명·잠실 4명(4인 테이블, 지역 최소 2테이블) → 홍대 2테이블만 채택하고 지역을 저장, 잠실은 다음 희망 회차가 있으면 REALLOCATING, 없으면 REGION_TOO_SMALL로 해결 선택")
    void runMatch_smallRegion_discarded() {
        // given
        applications = Stream.concat(applications(0, 8, DiningRegion.HONGDAE).stream(),
                applications(8, 12, DiningRegion.JAMSIL).stream()).toList();
        givenRun(2, List.of(applications.get(8), applications.get(9)));

        // when
        MatchRunResponse response = diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, null);

        // then
        assertThat(response.getTableCount()).isEqualTo(2);
        ArgumentCaptor<DiningTable> tables = ArgumentCaptor.forClass(DiningTable.class);
        then(diningTableRepository).should(times(2)).save(tables.capture());
        assertThat(tables.getAllValues()).extracting(DiningTable::getRegion).containsOnly("홍대/합정");
        assertThat(applications.subList(0, 8)).allSatisfy(a -> assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.CONFIRM_PENDING));
        assertThat(applications.subList(8, 10)).allSatisfy(a -> assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.REALLOCATING));
        assertThat(applications.subList(10, 12)).allSatisfy(a -> assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.ALTERNATIVE_OFFERED));
        MatchRun run = savedRun();
        assertThat(run.getUnassignedReasons()).containsExactly(
                new MatchRun.Unassigned(new UUID(0, 8), UnassignedReason.NEXT_SESSION_WAITING),
                new MatchRun.Unassigned(new UUID(0, 9), UnassignedReason.NEXT_SESSION_WAITING),
                new MatchRun.Unassigned(new UUID(0, 10), UnassignedReason.REGION_TOO_SMALL),
                new MatchRun.Unassigned(new UUID(0, 11), UnassignedReason.REGION_TOO_SMALL));
        assertThat(run.getRegionResults()).containsExactly(
                new MatchRun.RegionResult("홍대/합정", 8, 2, true),
                new MatchRun.RegionResult("잠실", 4, 1, false),
                new MatchRun.RegionResult("강남/신논현", 0, 0, false),
                new MatchRun.RegionResult("을지로/종로", 0, 0, false));
    }

    @Test
    @DisplayName("앞 지역 결과가 버려지면 두 지역을 고른 사람은 다음 지역 후보로 남아 그 지역 테이블에 앉는다")
    void runMatch_multiRegionApplicants_flowToNextRegion() {
        // given: 홍대만 4명(서로 같은 테이블 금지) + 홍대·잠실 4명 + 잠실만 4명. 후보 수·단일 지역 수가 같아 선언 순으로 홍대 먼저.
        List<Application> hongdaeOnly = applications(0, 4, DiningRegion.HONGDAE);
        List<Application> both = applications(4, 8, DiningRegion.HONGDAE, DiningRegion.JAMSIL);
        List<Application> jamsilOnly = applications(8, 12, DiningRegion.JAMSIL);
        applications = Stream.of(hongdaeOnly, both, jamsilOnly).flatMap(List::stream).toList();
        Map<UUID, Set<UUID>> pairs = new HashMap<>();
        for (Application a : hongdaeOnly) {
            Set<UUID> others = new HashSet<>();
            hongdaeOnly.stream().filter(b -> b != a).forEach(b -> others.add(b.getUser().getId()));
            pairs.put(a.getUser().getId(), others);
        }
        blocked = pairs;
        givenRun(2, List.of());

        // when
        MatchRunResponse response = diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, null);

        // then
        assertThat(response.getTableCount()).isEqualTo(2);
        ArgumentCaptor<DiningTable> tables = ArgumentCaptor.forClass(DiningTable.class);
        then(diningTableRepository).should(times(2)).save(tables.capture());
        assertThat(tables.getAllValues()).extracting(DiningTable::getRegion).containsOnly("잠실");
        assertThat(Stream.concat(both.stream(), jamsilOnly.stream()))
                .allSatisfy(a -> assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.CONFIRM_PENDING));
        MatchRun run = savedRun();
        assertThat(run.getUnassignedReasons()).extracting(MatchRun.Unassigned::reason)
                .hasSize(4).containsOnly(UnassignedReason.REGION_TOO_SMALL);
        assertThat(run.getRegionResults()).extracting(MatchRun.RegionResult::region, MatchRun.RegionResult::adopted)
                .startsWith(tuple("홍대/합정", false), tuple("잠실", true));
    }

    @Test
    @DisplayName("지역 순서는 후보 수 → 그 지역만 고른 사람 수 → 선언 순이고, 같은 입력이면 같은 결과다(무작위 없음)")
    void runMatch_regionOrder_deterministic() {
        // given: 16명. 홍대 8(홍대만 4), 잠실 8(잠실만 0), 강남 8(강남만 4) → 홍대 → 강남 → 잠실 순.
        applications = IntStream.range(0, 16).mapToObj(i -> switch (i % 4) {
            case 0 -> application(i, DiningRegion.HONGDAE);
            case 1 -> application(i, DiningRegion.HONGDAE, DiningRegion.JAMSIL);
            case 2 -> application(i, DiningRegion.JAMSIL, DiningRegion.GANGNAM);
            default -> application(i, DiningRegion.GANGNAM);
        }).toList();
        givenRun(2, List.of());
        given(diningTableRepository.save(any(DiningTable.class))).willAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<DiningTableMember> seats = ArgumentCaptor.forClass(DiningTableMember.class);

        // when: 같은 입력으로 두 번 실행
        diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, null);
        applications.forEach(a -> a.changeMatchStatus(MatchStatus.WAITING));
        diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, null);

        // then
        then(diningTableMemberRepository).should(times(32)).save(seats.capture());
        List<String> placements = seats.getAllValues().stream()
                .map(m -> m.getTable().getRegion() + ":" + m.getSeatOrder() + ":" + m.getApplication().getId())
                .toList();
        assertThat(placements.subList(0, 16)).isEqualTo(placements.subList(16, 32));
        ArgumentCaptor<MatchRun> runs = ArgumentCaptor.forClass(MatchRun.class);
        then(matchRunRepository).should(times(2)).save(runs.capture());
        assertThat(runs.getAllValues().get(0).getRegionResults()).extracting(MatchRun.RegionResult::region)
                .containsExactly("홍대/합정", "강남/신논현", "잠실", "을지로/종로");
        assertThat(runs.getAllValues().get(0).getRegionResults())
                .isEqualTo(runs.getAllValues().get(1).getRegionResults());
    }

    @Test
    @DisplayName("확정 재검증: 테이블 지역을 희망하지 않는 멤버가 있으면 불통과, 지역 없는 과거 테이블은 지역을 보지 않는다")
    void satisfiesHardConditions_requiresRegionWish() {
        // given
        given(adminMatchingRuleService.findMatchingRuleSetting()).willReturn(MatchingRuleSetting.defaults());
        given(adminApplicationService.findAnswers(anyCollection())).willReturn(List.of());
        given(diningTableMemberRepository.findUserPairsByTableStatusIn(anyCollection(), anyCollection(), any()))
                .willReturn(List.of());
        given(matchExclusionProvider.findExcludedPairs(anyCollection())).willReturn(Map.of());
        List<Application> members = applications.subList(0, 4);

        // when & then
        assertThat(diningMatchService.satisfiesHardConditions(table("홍대/합정"), members)).isTrue();
        assertThat(diningMatchService.satisfiesHardConditions(table("잠실"), members)).isFalse();
        assertThat(diningMatchService.satisfiesHardConditions(table(null), members)).isTrue();
    }

    private DiningTable table(String region) {
        DiningTable table = DiningTable.builder().session(session).eventDate(session.getEventDate()).region(region)
                .groupSize(4).algorithmVersion("rule-v2").build();
        ReflectionTestUtils.setField(table, "id", UUID.randomUUID());
        return table;
    }

    @Test
    @DisplayName("보증금 대상인데 보증금 입금 전인 신청은 매칭 후보에서 빠진다 (KAN-402)")
    void matchCandidates_excludeUnpaidDeposit() {
        givenRun(false);
        given(depositService.findUnsatisfiedApplicationIds(anyCollection())).willReturn(Set.of(new UUID(0, 0)));

        MatchRunResponse response = diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, null);

        assertThat(response.getCandidateCount()).isEqualTo(4);
        assertThat(response.getUnassignedCount()).isZero();
        assertThat(applications.get(0).getMatchStatus()).isEqualTo(MatchStatus.WAITING);
        assertThat(applications.subList(1, 5)).allSatisfy(a -> assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.CONFIRM_PENDING));
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
