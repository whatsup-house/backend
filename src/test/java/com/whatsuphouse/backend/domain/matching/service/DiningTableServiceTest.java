package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.chat.service.AdminChatService;
import com.whatsuphouse.backend.domain.dining.entity.MatchingWeights;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.dining.service.SessionVenueService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableAdjustResponse;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableListResponse;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.matching.service.DiningMatchService.TableEvaluator;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DiningTableServiceTest {

    // 테이블 인원 3~5명, 출생연도 차 8년 이하. 답변이 없어 점수는 0이다.
    private static final MatchingEngine.Rules RULES =
            new MatchingEngine.Rules(3, 5, 8, 0, MatchingWeights.defaults(), List.of());

    @Mock
    private DiningTableRepository diningTableRepository;
    @Mock
    private DiningTableMemberRepository diningTableMemberRepository;
    @Mock
    private DiningMatchService diningMatchService;
    @Mock
    private AdminApplicationService adminApplicationService;
    @Mock
    private SessionVenueService sessionVenueService;
    @Mock
    private ExceptionCaseService exceptionCaseService;
    @Mock
    private AdminChatService adminChatService;

    @InjectMocks
    private DiningTableService diningTableService;

    private final UUID adminId = UUID.randomUUID();
    private final Map<UUID, Integer> birthYears = new HashMap<>();
    private Gathering gathering;
    private GatheringSession session;
    private int seq;

    @BeforeEach
    void setUp() {
        gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        session = session();
        // 평가기는 넘어온 신청들로 출생연도만 채운 프로필을 만든다(실제 DiningMatchService.evaluator와 같은 역할).
        lenient().when(diningMatchService.evaluator(any(), anyCollection(), any())).thenAnswer(invocation -> {
            Collection<Application> applications = invocation.getArgument(1);
            Map<UUID, MatchingEngine.Applicant> profiles = applications.stream().collect(Collectors.toMap(
                    Application::getId,
                    a -> new MatchingEngine.Applicant(a.getId(), a.getUser().getId(), birthYears.get(a.getId()),
                            null, null, Set.of(), Set.of(), Set.of(), null, Map.of()),
                    (a, b) -> a));
            return new TableEvaluator(new MatchingEngine(), profiles, RULES, MatchingEngine.Relations.none());
        });
        lenient().when(diningMatchService.getTables(any()))
                .thenReturn(DiningTableListResponse.builder().tables(List.of()).build());
    }

    private GatheringSession session() {
        GatheringSession s = GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().plusDays(7)).maxAttendees(20).build();
        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
        return s;
    }

    private Application application(int birthYear) {
        int no = ++seq;
        User user = User.builder().email("u" + no + "@example.com").password("pw").name("회원" + no)
                .gender(Gender.MALE).age(30).nickname("회원" + no).build();
        ReflectionTestUtils.setField(user, "id", new UUID(1, no));
        user.approveRandomTable();
        Application application = Application.builder().bookingNumber("WH-" + no).gathering(gathering).user(user)
                .name("회원" + no).phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", new UUID(0, no));
        application.confirm();
        birthYears.put(application.getId(), birthYear);
        return application;
    }

    private DiningTable table(GatheringSession s, DiningTableStatus status) {
        DiningTable table = DiningTable.builder().session(s).eventDate(s.getEventDate()).groupSize(0)
                .algorithmVersion("rule-v2").build();
        ReflectionTestUtils.setField(table, "id", UUID.randomUUID());
        if (status == DiningTableStatus.CONFIRMED) {
            table.confirm();
        }
        lenient().when(diningTableRepository.findByIdForUpdate(table.getId())).thenReturn(Optional.of(table));
        return table;
    }

    private DiningTableMember member(DiningTable table, Application application) {
        DiningTableMember member = DiningTableMember.builder().application(application).table(table)
                .seatOrder(1).assignReason(AssignReason.INITIAL).isManual(false).build();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        return member;
    }

    private List<DiningTableMember> members(DiningTable table, int count) {
        return IntStream.range(0, count).mapToObj(i -> member(table, application(1995))).toList();
    }

    private List<String> actions(DiningTable table) {
        return table.getReallocationLog().stream().map(entry -> (String) entry.get("action")).toList();
    }

    // ── 확정 후 취소 재조정 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("재조정 (1) 충원: 취소 멤버는 행을 남긴 채 removed_at만 채우고, 최소 인원 미만이면 REALLOCATING 대기자를 REALLOCATED로 채우고 채팅방에 반영")
    void rebalance_refillsFromReallocating() {
        DiningTable table = table(session, DiningTableStatus.CONFIRMED);
        UUID roomId = UUID.randomUUID();
        ReflectionTestUtils.setField(table, "chatRoomId", roomId);
        List<DiningTableMember> seated = members(table, 3);
        Application cancelled = seated.get(2).getApplication();
        cancelled.cancel();
        Application waiting = application(1996);
        waiting.changeMatchStatus(MatchStatus.REALLOCATING);
        Application tooOld = application(1980);
        tooOld.changeMatchStatus(MatchStatus.REALLOCATING);
        given(diningTableMemberRepository.findByTableIdWithApplication(table.getId())).willReturn(seated);
        given(adminApplicationService.listSessionApplications(session.getId())).willReturn(List.of(tooOld, waiting));
        given(diningTableMemberRepository.findApplicationIdsByTableStatusIn(anyCollection(), anyCollection()))
                .willReturn(List.of());
        // 저장 시 JPA가 ID를 채우는 것을 흉내 낸다.
        given(diningTableMemberRepository.save(any(DiningTableMember.class))).willAnswer(invocation -> {
            DiningTableMember saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        diningTableService.rebalance(table.getId());

        assertThat(seated.get(2).getRemovedAt()).isNotNull();
        assertThat(seated.subList(0, 2)).allSatisfy(m -> assertThat(m.getRemovedAt()).isNull());
        then(diningTableMemberRepository).should(never()).deleteAll(any());
        ArgumentCaptor<DiningTableMember> added = ArgumentCaptor.forClass(DiningTableMember.class);
        then(diningTableMemberRepository).should().save(added.capture());
        assertThat(added.getValue().getApplication()).isSameAs(waiting);
        assertThat(added.getValue().getAssignReason()).isEqualTo(AssignReason.REALLOCATED);
        assertThat(waiting.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(tooOld.getMatchStatus()).isEqualTo(MatchStatus.REALLOCATING);
        assertThat(table.getGroupSize()).isEqualTo(3);
        assertThat(actions(table)).containsExactly("CANCEL_REMOVE", "REFILL");
        then(adminChatService).should().syncTableMembers(roomId, List.of(), List.of(cancelled.getUser().getId()));
        then(adminChatService).should().syncTableMembers(roomId, List.of(waiting.getUser().getId()), List.of());
        then(exceptionCaseService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("재조정 (2) 이동: 충원할 대기자가 없으면 남은 멤버를 다른 테이블로 옮기고 원 테이블은 해체(식당 해제)")
    void rebalance_movesRemainingToOtherTable() {
        DiningTable table = table(session, DiningTableStatus.PROPOSED);
        UUID venueId = UUID.randomUUID();
        table.assignVenue(venueId);
        List<DiningTableMember> seated = members(table, 3);
        seated.get(2).getApplication().cancel();
        DiningTable other = table(session, DiningTableStatus.PROPOSED);
        List<DiningTableMember> otherMembers = members(other, 3);
        given(diningTableMemberRepository.findByTableIdWithApplication(table.getId())).willReturn(seated);
        given(adminApplicationService.listSessionApplications(session.getId())).willReturn(List.of());
        given(diningTableRepository.findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(eq(session.getId()), anyCollection()))
                .willReturn(List.of(table, other));
        given(diningTableMemberRepository.findByTableIdsWithApplication(List.of(other.getId()))).willReturn(otherMembers);

        diningTableService.rebalance(table.getId());

        assertThat(table.getStatus()).isEqualTo(DiningTableStatus.DISSOLVED);
        then(sessionVenueService).should().releaseTable(session.getId(), venueId);
        assertThat(seated.subList(0, 2)).allSatisfy(member -> {
            assertThat(member.getTable()).isSameAs(other);
            assertThat(member.getAssignReason()).isEqualTo(AssignReason.REALLOCATED);
            assertThat(member.getApplication().getMatchStatus()).isEqualTo(MatchStatus.CONFIRM_PENDING);
        });
        assertThat(other.getGroupSize()).isEqualTo(5);
        assertThat(actions(other)).containsExactly("REBALANCE_IN");
        then(exceptionCaseService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("재조정 (3) 예외함: 충원도 이동도 안 되면 CONFLICT 예외 건을 연다")
    void rebalance_opensConflictWhenUnresolvable() {
        DiningTable table = table(session, DiningTableStatus.CONFIRMED);
        List<DiningTableMember> seated = members(table, 3);
        seated.get(2).getApplication().cancel();
        given(diningTableMemberRepository.findByTableIdWithApplication(table.getId())).willReturn(seated);
        given(adminApplicationService.listSessionApplications(session.getId())).willReturn(List.of());
        given(diningTableRepository.findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(eq(session.getId()), anyCollection()))
                .willReturn(List.of(table));

        diningTableService.rebalance(table.getId());

        then(exceptionCaseService).should().open(eq(ExceptionCaseType.CONFLICT), eq(session.getId()), eq(table.getId()),
                isNull(), anyString());
        assertThat(table.getStatus()).isEqualTo(DiningTableStatus.CONFIRMED);
        assertThat(table.getGroupSize()).isEqualTo(2);
    }

    // ── 수동 조정 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이동: 원 테이블이 최소 인원 미만이 되면 400 TABLE_RULE_VIOLATION + violations")
    void move_violation_badRequest() {
        DiningTable source = table(session, DiningTableStatus.PROPOSED);
        DiningTable target = table(session, DiningTableStatus.PROPOSED);
        List<DiningTableMember> sourceMembers = members(source, 3);
        List<DiningTableMember> targetMembers = members(target, 3);
        DiningTableMember moving = sourceMembers.get(0);
        given(diningTableMemberRepository.findByIdAndRemovedAtIsNull(moving.getId())).willReturn(Optional.of(moving));
        given(diningTableMemberRepository.countByTable_IdAndRemovedAtIsNull(target.getId())).willReturn(3);
        given(diningTableMemberRepository.findByTableIdsWithApplication(List.of(source.getId(), target.getId())))
                .willReturn(Stream.concat(sourceMembers.stream(), targetMembers.stream()).toList());

        assertThatThrownBy(() -> diningTableService.moveMember(source.getId(), moving.getId(), target.getId(), null, adminId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TABLE_RULE_VIOLATION)
                .satisfies(e -> {
                    @SuppressWarnings("unchecked")
                    List<DiningTableAdjustResponse.Violation> violations =
                            (List<DiningTableAdjustResponse.Violation>) ((CustomException) e).getParams().get("violations");
                    assertThat(violations).extracting(DiningTableAdjustResponse.Violation::getTableId,
                                    DiningTableAdjustResponse.Violation::getRule)
                            .containsExactly(tuple(source.getId(), MatchingEngine.HardRule.TABLE_SIZE));
                });
        assertThat(source.isLocked()).isFalse();
    }

    @Test
    @DisplayName("이동: 통과하면 점수 재계산·잠금·수동 표시·이력 기록")
    void move_valid_locksAndRecords() {
        DiningTable source = table(session, DiningTableStatus.PROPOSED);
        DiningTable target = table(session, DiningTableStatus.PROPOSED);
        List<DiningTableMember> sourceMembers = members(source, 4);
        List<DiningTableMember> targetMembers = members(target, 3);
        DiningTableMember moving = sourceMembers.get(0);
        given(diningTableMemberRepository.findByIdAndRemovedAtIsNull(moving.getId())).willReturn(Optional.of(moving));
        given(diningTableMemberRepository.countByTable_IdAndRemovedAtIsNull(target.getId())).willReturn(3);
        given(diningTableMemberRepository.findByTableIdsWithApplication(List.of(source.getId(), target.getId())))
                .willReturn(Stream.concat(sourceMembers.stream(), targetMembers.stream()).toList());

        DiningTableAdjustResponse response = diningTableService.moveMember(
                source.getId(), moving.getId(), target.getId(), " 지인 분리 ", adminId);

        assertThat(response.getValidation().isValid()).isTrue();
        assertThat(moving.getTable()).isSameAs(target);
        assertThat(moving.isManual()).isTrue();
        assertThat(moving.getAssignReason()).isEqualTo(AssignReason.MANUAL);
        assertThat(source.getGroupSize()).isEqualTo(3);
        assertThat(target.getGroupSize()).isEqualTo(4);
        assertThat(source.isLocked()).isTrue();
        assertThat(target.isLocked()).isTrue();
        Map<String, Object> log = target.getReallocationLog().get(0);
        assertThat(log).containsEntry("action", "MOVE").containsEntry("by", adminId.toString())
                .containsEntry("reason", "지인 분리").containsEntry("memberIds", List.of(moving.getId().toString()));
    }

    @Test
    @DisplayName("이동: 다른 회차 테이블로는 400")
    void move_otherSession_badRequest() {
        DiningTable source = table(session, DiningTableStatus.PROPOSED);
        DiningTable target = table(session(), DiningTableStatus.PROPOSED);

        assertThatThrownBy(() -> diningTableService.moveMember(source.getId(), UUID.randomUUID(), target.getId(), null, adminId))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TABLE_SESSION_MISMATCH);
    }

    @Test
    @DisplayName("병합: 합친 인원이 최대 인원을 넘으면 400 TABLE_RULE_VIOLATION")
    void merge_overCapacity_badRequest() {
        DiningTable first = table(session, DiningTableStatus.PROPOSED);
        DiningTable second = table(session, DiningTableStatus.PROPOSED);
        List<DiningTableMember> firstMembers = members(first, 4);
        List<DiningTableMember> secondMembers = members(second, 3);
        given(diningTableMemberRepository.findByTableIdsWithApplication(List.of(second.getId()))).willReturn(secondMembers);
        given(diningTableMemberRepository.countByTable_IdAndRemovedAtIsNull(first.getId())).willReturn(4);
        given(diningTableMemberRepository.findByTableIdsWithApplication(List.of(first.getId())))
                .willReturn(Stream.concat(firstMembers.stream(), secondMembers.stream()).toList());

        assertThatThrownBy(() -> diningTableService.mergeTables(List.of(first.getId(), second.getId()), null, adminId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TABLE_RULE_VIOLATION)
                .satisfies(e -> {
                    @SuppressWarnings("unchecked")
                    List<DiningTableAdjustResponse.Violation> violations =
                            (List<DiningTableAdjustResponse.Violation>) ((CustomException) e).getParams().get("violations");
                    assertThat(violations).singleElement().satisfies(v -> {
                        assertThat(v.getTableId()).isEqualTo(first.getId());
                        assertThat(v.getRule()).isEqualTo(MatchingEngine.HardRule.TABLE_SIZE);
                        assertThat(v.getMessage()).contains("7명");
                    });
                });
    }

    @Test
    @DisplayName("확정 테이블 조정은 사유가 없으면 400")
    void confirmedTable_requiresReason() {
        DiningTable confirmed = table(session, DiningTableStatus.CONFIRMED);
        DiningTable proposed = table(session, DiningTableStatus.PROPOSED);

        assertThatThrownBy(() -> diningTableService.dissolveTable(confirmed.getId(), null, adminId))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TABLE_ADJUST_REASON_REQUIRED);
        assertThatThrownBy(() -> diningTableService.moveMember(proposed.getId(), UUID.randomUUID(), confirmed.getId(), "  ", adminId))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TABLE_ADJUST_REASON_REQUIRED);
        assertThat(confirmed.getStatus()).isEqualTo(DiningTableStatus.CONFIRMED);
    }

    @Test
    @DisplayName("해체: 멤버를 REALLOCATING으로 돌리고 테이블을 DISSOLVED로")
    void dissolve_returnsMembersToReallocating() {
        DiningTable table = table(session, DiningTableStatus.CONFIRMED);
        List<DiningTableMember> seated = members(table, 3);
        given(diningTableMemberRepository.findByTableIdWithApplication(table.getId())).willReturn(seated);

        diningTableService.dissolveTable(table.getId(), "식당 사정", adminId);

        assertThat(table.getStatus()).isEqualTo(DiningTableStatus.DISSOLVED);
        assertThat(seated).allSatisfy(m -> assertThat(m.getApplication().getMatchStatus()).isEqualTo(MatchStatus.REALLOCATING));
        assertThat(actions(table)).containsExactly("DISSOLVE");
    }
}
