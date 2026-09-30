package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.chat.entity.ChatMessage;
import com.whatsuphouse.backend.domain.chat.enums.ChatSystemKind;
import com.whatsuphouse.backend.domain.chat.repository.ChatMessageRepository;
import com.whatsuphouse.backend.domain.chat.service.AdminChatService;
import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import com.whatsuphouse.backend.domain.dining.repository.SessionVenueRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableDetailResponse;
import com.whatsuphouse.backend.domain.matching.entity.Attendance;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.entity.MatchRun;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.enums.MatchRunTrigger;
import com.whatsuphouse.backend.domain.matching.repository.AttendanceRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.matching.repository.MatchRunRepository;
import com.whatsuphouse.backend.domain.matching.scheduler.DiningMatchScheduler;
import com.whatsuphouse.backend.domain.notification.entity.Notification;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.repository.NotificationRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.reset;

/**
 * 자동 확정 파이프라인(설계 4.6) 통합 테스트. 단계별 트랜잭션 경계("이후 단계 실패는 확정을 되돌리지 않음")를 실제 DB(H2)로 확인한다.
 * 테스트마다 새 회차·회원을 만들어 검증하고(트랜잭션 롤백 없음), 끝나면 공유 H2를 비운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "dining.system-admin-id=00000000-0000-0000-0000-00000000a001")
class DiningConfirmPipelineTest {

    @MockitoSpyBean
    private AdminChatService adminChatService;
    // H2에서는 application_answers.value가 예약어라 답변 조회가 실패한다. 답변 없음(점수 0, 나이 모름)으로 둔다.
    @MockitoSpyBean
    private AdminApplicationService adminApplicationService;

    @Autowired
    private DiningConfirmService diningConfirmService;
    @Autowired
    private DiningTableDetailService diningTableDetailService;
    @Autowired
    private DiningMatchScheduler diningMatchScheduler;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GatheringRepository gatheringRepository;
    @Autowired
    private GatheringSessionRepository gatheringSessionRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private ApplicationCandidateSessionRepository applicationCandidateSessionRepository;
    @Autowired
    private DiningTableRepository diningTableRepository;
    @Autowired
    private DiningTableMemberRepository diningTableMemberRepository;
    @Autowired
    private MatchRunRepository matchRunRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private SessionVenueRepository sessionVenueRepository;
    @Autowired
    private ExceptionCaseRepository exceptionCaseRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Gathering gathering;
    private GatheringSession session;

    // 회차: 2~4인 테이블, 최소 점수 0(답변이 없어 점수는 0), 유예 0분
    @BeforeEach
    void setUp() {
        willReturn(List.of()).given(adminApplicationService).findAnswers(any());
        gathering = gatheringRepository.save(
                Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
        session = newSession(null);
    }

    // 공유 H2(DB_CLOSE_DELAY=-1)에 커밋된 행이 남으면 캐시된 다른 컨텍스트의 테스트(AdminUserRepositoryTest 등)가 깨진다.
    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'", String.class)
                .forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\""));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    @DisplayName("유예 0분: 매칭 스케줄러가 만든 테이블을 확정 스케줄러가 바로 확정하고 식당·채팅방·참석·알림까지 만든다")
    void confirmDueTables_zeroGrace_confirmsImmediately() {
        // given
        GatheringSession due = newSession(LocalDateTime.now().minusMinutes(1));
        List<Application> applications = IntStream.range(0, 3).mapToObj(i -> waitingApplication(due)).toList();
        Venue venue = venueRepository.save(Venue.builder().name("와썹 비스트로").address("서울 강남구 테헤란로 1")
                .region("강남").isActive(true).build());
        sessionVenueRepository.save(new SessionVenue(due.getId(), venue.getId(), 1));

        // when
        diningMatchScheduler.runDueMatches();
        diningConfirmService.confirmDueTables();

        // then
        DiningTable table = diningTableRepository.findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(
                due.getId(), List.of(DiningTableStatus.CONFIRMED)).get(0);
        assertThat(gatheringSessionRepository.findById(due.getId()).orElseThrow().getStatus())
                .isEqualTo(GatheringSessionStatus.CLOSED);
        assertThat(table.getConfirmedAt()).isNotNull();
        assertThat(table.getVenueId()).isEqualTo(venue.getId());
        assertThat(sessionVenueRepository.findById(new SessionVenue.Key(due.getId(), venue.getId())).orElseThrow()
                .getUsedTables()).isEqualTo(1);

        applications.forEach(application -> {
            Application reloaded = applicationRepository.findById(application.getId()).orElseThrow();
            assertThat(reloaded.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
            assertThat(reloaded.getSession().getId()).isEqualTo(due.getId());
            List<Notification> notifications = notificationRepository
                    .findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(application.getUser().getId());
            assertThat(notifications).singleElement().satisfies(n -> {
                assertThat(n.getType()).isEqualTo(NotificationType.DINING_CONFIRMED);
                assertThat(n.getLink()).isEqualTo(NotificationLink.DINING_TABLE);
                assertThat(n.getLinkId()).isEqualTo(table.getId());
            });
        });
        List<UUID> memberIds = diningTableMemberRepository.findByTableIdWithApplication(table.getId()).stream()
                .map(DiningTableMember::getId).toList();
        assertThat(attendanceRepository.findByTableMemberIdIn(memberIds)).hasSize(3)
                .extracting(Attendance::getStatus).containsOnly(AttendanceStatus.SCHEDULED);

        assertThat(table.getChatRoomId()).isNotNull();
        ChatMessage notice = chatMessageRepository.findFirstByRoomIdOrderByCreatedAtDescIdDesc(table.getChatRoomId())
                .orElseThrow();
        assertThat(notice.getSystemKind()).isEqualTo(ChatSystemKind.SYSTEM_NOTICE);
        assertThat(notice.getSystemParams().get("text").toString()).contains("와썹 비스트로", "취소 정책");
        assertThat(openCases(table.getId())).isEmpty();
    }

    @Test
    @DisplayName("같은 회차는 두 번 매칭하지 않는다: 마감된 회차는 다시 잡지 않고, 실행 기록이 있는 회차는 마감만 한다")
    void runDueMatches_skipsAlreadyRunSessions() {
        // given
        GatheringSession due = newSession(LocalDateTime.now().minusMinutes(1));
        IntStream.range(0, 2).forEach(i -> waitingApplication(due));
        GatheringSession manuallyRun = newSession(LocalDateTime.now().minusMinutes(1));
        MatchRun manual = MatchRun.start(manuallyRun.getId(), MatchRunTrigger.MANUAL, null, MatchingEngine.ALGORITHM_VERSION);
        manual.finish(0, 0, 0, 0, 0, List.of());
        matchRunRepository.save(manual);

        // when
        diningMatchScheduler.runDueMatches();
        diningMatchScheduler.runDueMatches();

        // then
        assertThat(countRuns(due.getId())).isEqualTo(1);
        assertThat(countRuns(manuallyRun.getId())).isEqualTo(1);
        assertThat(gatheringSessionRepository.findById(manuallyRun.getId()).orElseThrow().getStatus())
                .isEqualTo(GatheringSessionStatus.CLOSED);
    }

    @Test
    @DisplayName("회차 식당 풀에 자리가 없으면 확정은 유지하고 예외함에 VENUE를 남긴다")
    void confirmNow_noVenue_keepsConfirmedAndOpensVenueCase() {
        // given
        DiningTable table = proposedTable(3);

        // when
        diningConfirmService.confirmNow(table.getId());

        // then
        DiningTable reloaded = diningTableRepository.findById(table.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DiningTableStatus.CONFIRMED);
        assertThat(reloaded.getVenueId()).isNull();
        assertThat(reloaded.getChatRoomId()).isNotNull();
        assertThat(openCases(table.getId())).extracting(ExceptionCase::getType).containsExactly(ExceptionCaseType.VENUE);
    }

    @Test
    @DisplayName("채팅방 생성이 실패해도 확정은 유지하고 예외함에 NOTIFICATION을 남기며, 재시도가 성공하면 그 건을 닫는다")
    void confirmNow_chatFails_keepsConfirmedAndRetryResolves() {
        // given
        DiningTable table = proposedTable(3);
        willThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
                .given(adminChatService).createGroupRoom(any(), anyBoolean(), any());

        // when
        diningConfirmService.confirmNow(table.getId());

        // then
        DiningTable reloaded = diningTableRepository.findById(table.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DiningTableStatus.CONFIRMED);
        assertThat(reloaded.getChatRoomId()).isNull();
        assertThat(openCases(table.getId())).filteredOn(c -> c.getType() == ExceptionCaseType.NOTIFICATION)
                .singleElement().satisfies(c -> assertThat(c.getReason()).startsWith(DiningConfirmService.CHAT_FAILURE_PREFIX));
        // 알림 단계는 채팅 실패와 무관하게 진행된다
        assertThat(notificationRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(memberUserId(table)))
                .hasSize(1);

        // when: 장애가 풀린 뒤 재시도
        reset(adminChatService);
        UUID adminId = UUID.randomUUID();
        UUID roomId = diningConfirmService.retryChatRoom(table.getId(), adminId).getRoomId();

        // then
        assertThat(diningTableRepository.findById(table.getId()).orElseThrow().getChatRoomId()).isEqualTo(roomId);
        assertThat(exceptionCaseRepository.findAllByTypeAndTableIdAndStatus(
                ExceptionCaseType.NOTIFICATION, table.getId(), ExceptionCaseStatus.RESOLVED))
                .singleElement().satisfies(c -> assertThat(c.getResolutionNote()).isEqualTo("재시도 성공"));
    }

    @Test
    @DisplayName("하드 조건(최소 인원) 위반이면 확정을 보류하고, 여러 번 돌아도 CONFLICT는 한 건만 남는다")
    void confirmDueTables_hardConditionViolated_holdsWithSingleConflict() {
        // given: 최소 2인 회차에 1명 테이블
        DiningTable table = proposedTable(1);
        LocalDateTime confirmAt = diningTableRepository.findById(table.getId()).orElseThrow().getConfirmAt();

        // when
        diningConfirmService.confirmDueTables();
        diningConfirmService.confirmDueTables();

        // then
        DiningTable reloaded = diningTableRepository.findById(table.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DiningTableStatus.PROPOSED);
        assertThat(reloaded.getConfirmAt()).isEqualTo(confirmAt);
        assertThat(openCases(table.getId())).extracting(ExceptionCase::getType).containsExactly(ExceptionCaseType.CONFLICT);
        assertThat(attendanceRepository.findByTableMemberIdIn(
                diningTableMemberRepository.findByTableIdWithApplication(table.getId()).stream()
                        .map(DiningTableMember::getId).toList())).isEmpty();
    }

    @Test
    @DisplayName("이미 확정된 테이블을 즉시 확정하면 409")
    void confirmNow_alreadyConfirmed_throwsConflict() {
        // given
        DiningTable table = proposedTable(2);
        diningConfirmService.confirmNow(table.getId());

        // when & then
        assertThatThrownBy(() -> diningConfirmService.confirmNow(table.getId()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.TABLE_NOT_PROPOSED);
    }

    @Test
    @DisplayName("테이블 상세: 멤버에게는 닉네임·MBTI·관심사와 내 참석을 보여주고, 멤버가 아니거나 확정 전이면 403, 없으면 404")
    void getTable_accessRules() {
        // given
        DiningTable table = proposedTable(2);
        UUID memberId = memberUserId(table);
        User outsider = user();

        // when & then: 확정 전
        assertThatThrownBy(() -> diningTableDetailService.getTable(table.getId(), memberId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DINING_TABLE_FORBIDDEN);

        diningConfirmService.confirmNow(table.getId());

        DiningTableDetailResponse detail = diningTableDetailService.getTable(table.getId(), memberId);
        assertThat(detail.getStatus()).isEqualTo(DiningTableStatus.CONFIRMED);
        assertThat(detail.getSession().getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(detail.getMembers()).hasSize(2).allSatisfy(m -> assertThat(m.getNickname()).isNotBlank())
                .extracting(DiningTableDetailResponse.MemberView::getUserId).contains(memberId);
        assertThat(detail.getMyAttendance().getStatus()).isEqualTo(AttendanceStatus.SCHEDULED);
        assertThat(detail.getCancelPolicy()).isEqualTo(DiningTableDetailService.CANCEL_POLICY);

        assertThatThrownBy(() -> diningTableDetailService.getTable(table.getId(), outsider.getId()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DINING_TABLE_FORBIDDEN);
        assertThatThrownBy(() -> diningTableDetailService.getTable(UUID.randomUUID(), memberId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MATCHING_GROUP_NOT_FOUND);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private GatheringSession newSession(LocalDateTime matchRunAt) {
        GatheringSession s = GatheringSession.builder().gathering(gathering).eventDate(LocalDate.now().plusDays(7))
                .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(21, 0)).maxAttendees(12).build();
        s.changeMatchingRules(matchRunAt, 0, 2, 4, BigDecimal.ZERO, null);
        return gatheringSessionRepository.save(s);
    }

    private User user() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder().email(key + "@example.com").password("encoded").name("회원" + key)
                .gender(Gender.MALE).age(30).nickname("회원" + key).build();
        user.approveRandomTable();
        return userRepository.save(user);
    }

    // 결제 완료·매칭 대기 신청 + 희망 회차
    private Application waitingApplication(GatheringSession wish) {
        User user = user();
        Application application = Application.builder().bookingNumber("WH-" + user.getNickname()).gathering(gathering)
                .user(user).name(user.getName()).phone("01012345678").build();
        application.confirm();
        application = applicationRepository.save(application);
        applicationCandidateSessionRepository.save(new ApplicationCandidateSession(application, wish, 1));
        return application;
    }

    // 확정 시각이 이미 지난 제안 테이블
    private DiningTable proposedTable(int memberCount) {
        DiningTable table = diningTableRepository.save(DiningTable.builder().session(session).eventDate(session.getEventDate())
                .groupSize(memberCount).algorithmVersion(MatchingEngine.ALGORITHM_VERSION)
                .confirmAt(LocalDateTime.now().minusMinutes(1)).build());
        for (int seat = 1; seat <= memberCount; seat++) {
            Application application = waitingApplication(session);
            application.changeMatchStatus(MatchStatus.CONFIRM_PENDING);
            diningTableMemberRepository.save(DiningTableMember.builder().application(applicationRepository.save(application))
                    .table(table).seatOrder(seat).assignReason(AssignReason.INITIAL).build());
        }
        return table;
    }

    private UUID memberUserId(DiningTable table) {
        return diningTableMemberRepository.findByTableIdWithApplication(table.getId()).get(0)
                .getApplication().getUser().getId();
    }

    private List<ExceptionCase> openCases(UUID tableId) {
        return exceptionCaseRepository.findAll().stream()
                .filter(c -> tableId.equals(c.getTableId()) && c.getStatus() == ExceptionCaseStatus.OPEN)
                .toList();
    }

    private long countRuns(UUID sessionId) {
        return matchRunRepository.findAll().stream().filter(run -> run.getSessionId().equals(sessionId)).count();
    }
}
