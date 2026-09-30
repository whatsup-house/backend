package com.whatsuphouse.backend.domain.dining.client.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.dining.client.dto.request.FeedbackCreateRequest;
import com.whatsuphouse.backend.domain.dining.client.dto.request.SafetyReportCreateRequest;
import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.entity.Feedback;
import com.whatsuphouse.backend.domain.dining.entity.PeerPreference;
import com.whatsuphouse.backend.domain.dining.entity.SafetyReport;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.enums.PeerPreferenceKind;
import com.whatsuphouse.backend.domain.dining.enums.RejoinIntent;
import com.whatsuphouse.backend.domain.dining.repository.FeedbackRepository;
import com.whatsuphouse.backend.domain.dining.repository.PeerPreferenceRepository;
import com.whatsuphouse.backend.domain.dining.repository.SafetyReportRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DiningServiceTest {

    @Mock
    private MatchingService matchingService;
    @Mock
    private ExceptionCaseService exceptionCaseService;
    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private PeerPreferenceRepository peerPreferenceRepository;
    @Mock
    private SafetyReportRepository safetyReportRepository;
    @Mock
    private VenueRepository venueRepository;

    @InjectMocks
    private DiningService diningService;

    private final Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
    private final UUID tableId = UUID.randomUUID();
    private DiningTable table;
    private List<DiningTableMember> members;

    // 회원 0·1·2가 앉은 확정 테이블, 회차는 어제
    @BeforeEach
    void setUp() {
        GatheringSession session = GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().minusDays(1)).maxAttendees(12).build();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        table = confirmedTable(session);
        ReflectionTestUtils.setField(table, "id", tableId);
        members = IntStream.range(0, 3).mapToObj(i -> {
            User user = User.builder().email("u" + i + "@example.com").password("pw").name("회원" + i)
                    .gender(Gender.MALE).age(30).nickname("닉네임" + i).build();
            ReflectionTestUtils.setField(user, "id", userId(i));
            Application application = Application.builder().bookingNumber("WH-" + i).gathering(gathering).user(user)
                    .name("회원" + i).phone("01012345678").build();
            ReflectionTestUtils.setField(application, "id", new UUID(0, i));
            DiningTableMember member = DiningTableMember.builder().application(application).table(table)
                    .seatOrder(i + 1).assignReason(AssignReason.INITIAL).build();
            ReflectionTestUtils.setField(member, "id", new UUID(2, i));
            return member;
        }).toList();
    }

    private void givenTable() {
        given(matchingService.findTable(tableId)).willReturn(table);
        given(matchingService.listActiveTableMembers(tableId)).willReturn(members);
    }

    private static DiningTable confirmedTable(GatheringSession session) {
        DiningTable t = DiningTable.builder().session(session).eventDate(session.getEventDate()).groupSize(3)
                .algorithmVersion("rule-v2").build();
        t.confirm();
        return t;
    }

    private static UUID userId(int no) {
        return new UUID(1, no);
    }

    private static FeedbackCreateRequest feedback(FeedbackCreateRequest.Peer... peers) {
        return new FeedbackCreateRequest(5, 4, 3, RejoinIntent.YES, " 즐거웠어요 ", List.of(peers));
    }

    private static FeedbackCreateRequest.Peer peer(int no, PeerPreferenceKind kind) {
        return new FeedbackCreateRequest.Peer(userId(no), kind);
    }

    private static void assertError(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", errorCode);
    }

    @Test
    @DisplayName("피드백과 사람별 선호를 함께 저장한다")
    @SuppressWarnings("unchecked")
    void submitFeedback_savesFeedbackAndPeers() {
        givenTable();

        diningService.submitFeedback(tableId, userId(0),
                feedback(peer(1, PeerPreferenceKind.AGAIN), peer(2, PeerPreferenceKind.AVOID)));

        ArgumentCaptor<Feedback> saved = ArgumentCaptor.forClass(Feedback.class);
        then(feedbackRepository).should().save(saved.capture());
        assertThat(saved.getValue().getTableMemberId()).isEqualTo(members.get(0).getId());
        assertThat(saved.getValue().getComment()).isEqualTo("즐거웠어요");
        ArgumentCaptor<List<PeerPreference>> prefs = ArgumentCaptor.forClass(List.class);
        then(peerPreferenceRepository).should().saveAll(prefs.capture());
        assertThat(prefs.getValue())
                .extracting(PeerPreference::getFromUserId, PeerPreference::getToUserId, PeerPreference::getKind,
                        PeerPreference::getTableId)
                .containsExactly(tuple(userId(0), userId(1), PeerPreferenceKind.AGAIN, tableId),
                        tuple(userId(0), userId(2), PeerPreferenceKind.AVOID, tableId));
    }

    @Test
    @DisplayName("테이블 멤버가 아니면 403")
    void submitFeedback_notMember_forbidden() {
        givenTable();

        assertError(() -> diningService.submitFeedback(tableId, userId(9), feedback()), ErrorCode.NOT_TABLE_MEMBER);
        then(feedbackRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 피드백을 남긴 멤버는 409")
    void submitFeedback_duplicate_conflict() {
        givenTable();
        given(feedbackRepository.existsByTableMemberId(members.get(0).getId())).willReturn(true);

        assertError(() -> diningService.submitFeedback(tableId, userId(0), feedback()),
                ErrorCode.FEEDBACK_ALREADY_SUBMITTED);
        then(feedbackRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("peers에 자기 자신·다른 테이블 회원·같은 사람 두 번이 있으면 400이고 아무것도 저장하지 않는다")
    void submitFeedback_invalidPeers_badRequest() {
        givenTable();

        assertError(() -> diningService.submitFeedback(tableId, userId(0), feedback(peer(0, PeerPreferenceKind.AGAIN))),
                ErrorCode.INVALID_TABLE_PEER);
        assertError(() -> diningService.submitFeedback(tableId, userId(0), feedback(peer(9, PeerPreferenceKind.AVOID))),
                ErrorCode.INVALID_TABLE_PEER);
        assertError(() -> diningService.submitFeedback(tableId, userId(0),
                feedback(peer(1, PeerPreferenceKind.AGAIN), peer(1, PeerPreferenceKind.AVOID))), ErrorCode.INVALID_TABLE_PEER);
        then(feedbackRepository).should(never()).save(any());
        then(peerPreferenceRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("아직 끝나지 않은 회차면 400")
    void submitFeedback_notOpen_badRequest() {
        givenTable();
        ReflectionTestUtils.setField(table.getSession(), "eventDate", LocalDate.now().plusDays(1));

        assertError(() -> diningService.submitFeedback(tableId, userId(0), feedback()), ErrorCode.FEEDBACK_NOT_OPEN);
    }

    @Test
    @DisplayName("확정·종료 테이블이고 회차 날짜가 지났거나 당일 종료 시각이 지났을 때만 피드백을 받는다")
    void isFeedbackOpen() {
        LocalDate today = LocalDate.now();
        assertThat(DiningService.isFeedbackOpen(table, LocalDateTime.now())).isTrue();

        DiningTable todayTable = confirmedTable(GatheringSession.builder().gathering(gathering).eventDate(today)
                .endTime(LocalTime.of(21, 0)).maxAttendees(12).build());
        assertThat(DiningService.isFeedbackOpen(todayTable, today.atTime(20, 59))).isFalse();
        assertThat(DiningService.isFeedbackOpen(todayTable, today.atTime(21, 0))).isTrue();

        DiningTable noEndTime = confirmedTable(GatheringSession.builder().gathering(gathering).eventDate(today)
                .maxAttendees(12).build());
        assertThat(DiningService.isFeedbackOpen(noEndTime, today.atTime(23, 59))).isFalse();

        DiningTable proposed = DiningTable.builder().session(table.getSession()).eventDate(table.getEventDate())
                .groupSize(3).algorithmVersion("rule-v2").build();
        assertThat(DiningService.isFeedbackOpen(proposed, LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("신고하면 피신고자 신청으로 SAFETY 예외를 만들고 신고에 연결한다")
    void reportMember_opensSafetyExceptionCase() {
        givenTable();
        ExceptionCase exceptionCase = ExceptionCase.open(ExceptionCaseType.SAFETY, null, null, null, "사유");
        ReflectionTestUtils.setField(exceptionCase, "id", UUID.randomUUID());
        given(exceptionCaseService.open(eq(ExceptionCaseType.SAFETY), eq(table.getSession().getId()), eq(tableId),
                eq(new UUID(0, 2)), anyString())).willReturn(exceptionCase);

        diningService.reportMember(tableId, userId(0), new SafetyReportCreateRequest(userId(2), " 불쾌한 언행 "));

        ArgumentCaptor<SafetyReport> saved = ArgumentCaptor.forClass(SafetyReport.class);
        then(safetyReportRepository).should().save(saved.capture());
        assertThat(saved.getValue())
                .extracting(SafetyReport::getReporterId, SafetyReport::getReportedUserId, SafetyReport::getTableId,
                        SafetyReport::getReason, SafetyReport::getExceptionCaseId)
                .containsExactly(userId(0), userId(2), tableId, "불쾌한 언행", exceptionCase.getId());
    }

    @Test
    @DisplayName("자기 자신 신고·같은 테이블 멤버가 아닌 대상은 400, 신고자가 멤버가 아니면 403")
    void reportMember_invalidTargets() {
        givenTable();

        assertError(() -> diningService.reportMember(tableId, userId(0), new SafetyReportCreateRequest(userId(0), "사유")),
                ErrorCode.SELF_REPORT_NOT_ALLOWED);
        assertError(() -> diningService.reportMember(tableId, userId(0), new SafetyReportCreateRequest(userId(9), "사유")),
                ErrorCode.INVALID_TABLE_PEER);
        assertError(() -> diningService.reportMember(tableId, userId(9), new SafetyReportCreateRequest(userId(0), "사유")),
                ErrorCode.NOT_TABLE_MEMBER);
        then(exceptionCaseService).shouldHaveNoInteractions();
        then(safetyReportRepository).shouldHaveNoInteractions();
    }
}
