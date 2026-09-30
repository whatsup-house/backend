package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.chat.entity.ChatMessage;
import com.whatsuphouse.backend.domain.chat.repository.ChatMessageRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningAttendanceResponse;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableDetailResponse;
import com.whatsuphouse.backend.domain.matching.entity.Attendance;
import com.whatsuphouse.backend.domain.matching.entity.DiningContent;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import com.whatsuphouse.backend.domain.matching.enums.DiningContentKind;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.repository.AttendanceRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningContentRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.notification.entity.Notification;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.repository.NotificationRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willReturn;

/**
 * 확정 이후 흐름(설계 4.9, KAN-349): 체크인, 24시간 전 리마인드, 시작 시각 대화 콘텐츠, 종료 후 노쇼 후보·완료 처리, 운영자 참석 확정.
 * 스케줄러·알림 리스너의 트랜잭션 경계를 실제 DB(H2)로 확인한다. 끝나면 공유 H2를 비운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "dining.system-admin-id=00000000-0000-0000-0000-00000000a001")
class DiningLifecycleTest {

    // H2에서는 application_answers.value가 예약어라 답변 조회가 실패한다. 답변 없음으로 둔다.
    @MockitoSpyBean
    private AdminApplicationService adminApplicationService;

    @Autowired
    private DiningConfirmService diningConfirmService;
    @Autowired
    private DiningAttendanceService diningAttendanceService;
    @Autowired
    private DiningReminderService diningReminderService;

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
    private AttendanceRepository attendanceRepository;
    @Autowired
    private DiningContentRepository diningContentRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Gathering gathering;

    @BeforeEach
    void setUp() {
        willReturn(List.of()).given(adminApplicationService).findAnswers(any());
        gathering = gatheringRepository.save(
                Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'", String.class)
                .forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\""));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    @DisplayName("체크인: 시작 ±2시간 안에서만 ATTENDED, 다시 하면 그대로(멱등), 멤버가 아니면 403, 창 밖이면 400, 테이블이 없으면 404")
    void checkIn_windowIdempotencyAndAccess() {
        // given
        LocalDateTime now = LocalDateTime.now();
        DiningTable table = confirmedTable(session(now.plusHours(1), null), 2);
        DiningTable later = confirmedTable(session(now.plusHours(3), null), 2);
        UUID memberId = memberUserId(table, 0);

        // when
        DiningTableDetailResponse.AttendanceView first = diningAttendanceService.checkIn(table.getId(), memberId);
        LocalDateTime storedCheckedInAt = attendances(table).get(0).getCheckedInAt();
        DiningTableDetailResponse.AttendanceView again = diningAttendanceService.checkIn(table.getId(), memberId);

        // then: 두 번째 체크인은 저장된 시각을 덮어쓰지 않는다
        assertThat(first.getStatus()).isEqualTo(AttendanceStatus.ATTENDED);
        assertThat(storedCheckedInAt).isNotNull();
        assertThat(again.getStatus()).isEqualTo(AttendanceStatus.ATTENDED);
        assertThat(again.getCheckedInAt()).isEqualTo(storedCheckedInAt);
        assertThat(attendances(table).get(0).getCheckedInAt()).isEqualTo(storedCheckedInAt);
        // 다음 모집 알림 대상 조회에도 잡힌다
        assertThat(diningAttendanceService.findRecentAttendeeLocations(LocalDate.now().minusMonths(6)))
                .containsOnlyKeys(memberId);

        assertError(() -> diningAttendanceService.checkIn(table.getId(), user().getId()), ErrorCode.DINING_TABLE_FORBIDDEN);
        assertError(() -> diningAttendanceService.checkIn(later.getId(), memberUserId(later, 0)), ErrorCode.CHECKIN_WINDOW_CLOSED);
        assertError(() -> diningAttendanceService.checkIn(UUID.randomUUID(), memberId), ErrorCode.MATCHING_GROUP_NOT_FOUND);
    }

    @Test
    @DisplayName("리마인드: 시작 24시간 전 확정 테이블 멤버에게 DINING_REMINDER와 채팅 안내를 테이블마다 한 번만 보낸다")
    void sendDueReminders_oncePerTable() {
        // given
        DiningTable table = confirmedTable(session(LocalDateTime.now().plusHours(24), null), 2);

        // when
        diningReminderService.sendDueReminders();
        diningReminderService.sendDueReminders();

        // then
        assertThat(diningTableRepository.findById(table.getId()).orElseThrow().getReminderSentAt()).isNotNull();
        for (int i = 0; i < 2; i++) {
            assertThat(notifications(memberUserId(table, i), NotificationType.DINING_REMINDER)).singleElement()
                    .satisfies(n -> {
                        assertThat(n.getLink()).isEqualTo(NotificationLink.DINING_TABLE);
                        assertThat(n.getLinkId()).isEqualTo(table.getId());
                    });
        }
        assertThat(roomNotices(table)).filteredOn(text -> text.startsWith("우연한 식탁이 곧 열려요"))
                .singleElement().satisfies(text -> assertThat(text).contains("취소 정책"));
    }

    @Test
    @DisplayName("대화 콘텐츠: 시작 시각에 채팅방에 주제 3개 + 아이스브레이킹 1개를 한 번만 올린다")
    void postDueContents_oncePerTable() {
        // given
        diningContentRepository.saveAll(List.of(
                new DiningContent(DiningContentKind.TOPIC, null, "주제 A"),
                new DiningContent(DiningContentKind.TOPIC, null, "주제 B"),
                new DiningContent(DiningContentKind.TOPIC, null, "주제 C"),
                new DiningContent(DiningContentKind.ICEBREAKER, null, "아이스 A")));
        DiningTable table = confirmedTable(session(LocalDateTime.now(), null), 2);

        // when
        diningReminderService.postDueContents();
        diningReminderService.postDueContents();

        // then
        assertThat(roomNotices(table)).filteredOn(text -> text.startsWith("오늘의 대화 주제")).singleElement()
                .satisfies(text -> assertThat(text).contains("주제 A", "주제 B", "주제 C", "아이스브레이킹: 아이스 A"));
    }

    @Test
    @DisplayName("종료 처리: 종료 + 1시간이 지나면 체크인 없는 참석을 노쇼 후보로, 테이블·회차를 DONE으로, 멤버에게 피드백 요청을 한 번만")
    void closeFinishedSessions_marksNoShowAndCompletes() {
        // given: 5시간 전 시작, 2시간 전 종료. 첫 멤버만 참석 처리
        LocalDateTime now = LocalDateTime.now();
        GatheringSession session = session(now.minusHours(5), now.minusHours(2));
        DiningTable table = confirmedTable(session, 3);
        List<Attendance> attendances = attendances(table);
        Attendance attended = attendances.get(0);
        attended.checkIn(now.minusHours(4));
        attendanceRepository.save(attended);

        // when
        diningAttendanceService.closeFinishedSessions();
        diningAttendanceService.closeFinishedSessions();

        // then
        assertThat(attendances(table)).satisfiesExactly(
                a -> assertThat(a.isNoShowCandidate()).isFalse(),
                a -> assertThat(a.isNoShowCandidate()).isTrue(),
                a -> assertThat(a.isNoShowCandidate()).isTrue());
        assertThat(attendances(table)).extracting(Attendance::getStatus)
                .containsExactly(AttendanceStatus.ATTENDED, AttendanceStatus.SCHEDULED, AttendanceStatus.SCHEDULED);
        assertThat(diningTableRepository.findById(table.getId()).orElseThrow().getStatus()).isEqualTo(DiningTableStatus.DONE);
        GatheringSession reloaded = gatheringSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(GatheringSessionStatus.DONE);
        assertThat(reloaded.getClosingProcessedAt()).isNotNull();
        for (int i = 0; i < 3; i++) {
            assertThat(notifications(memberUserId(table, i), NotificationType.DINING_FEEDBACK_REQUEST)).singleElement()
                    .satisfies(n -> {
                        assertThat(n.getLink()).isEqualTo(NotificationLink.DINING_FEEDBACK_REQUEST);
                        assertThat(n.getLinkId()).isEqualTo(table.getId());
                    });
        }
    }

    @Test
    @DisplayName("운영자 참석 관리: 목록 조회, NO_SHOW 확정(변경자 기록), 시스템 전용 상태·사전 취소 참석 변경은 400, 없으면 404")
    void changeAttendanceStatus_adminRules() {
        // given
        GatheringSession session = session(LocalDateTime.now().plusDays(3), null);
        DiningTable table = confirmedTable(session, 2);
        List<Attendance> attendances = attendances(table);
        UUID adminId = user().getId();

        // when
        DiningAttendanceResponse changed = diningAttendanceService.changeAttendanceStatus(
                attendances.get(0).getId(), AttendanceStatus.NO_SHOW, adminId);

        // then
        assertThat(changed.getStatus()).isEqualTo(AttendanceStatus.NO_SHOW);
        assertThat(changed.getTableId()).isEqualTo(table.getId());
        assertThat(attendanceRepository.findById(attendances.get(0).getId()).orElseThrow().getUpdatedBy()).isEqualTo(adminId);
        assertThat(diningAttendanceService.listSessionAttendances(session.getId()))
                .extracting(DiningAttendanceResponse::getAttendanceId, DiningAttendanceResponse::getStatus)
                .containsExactly(
                        tuple(attendances.get(0).getId(), AttendanceStatus.NO_SHOW),
                        tuple(attendances.get(1).getId(), AttendanceStatus.SCHEDULED));

        assertError(() -> diningAttendanceService.changeAttendanceStatus(
                attendances.get(1).getId(), AttendanceStatus.SCHEDULED, adminId), ErrorCode.INVALID_STATUS_TRANSITION);
        assertError(() -> diningAttendanceService.changeAttendanceStatus(
                UUID.randomUUID(), AttendanceStatus.NO_SHOW, adminId), ErrorCode.ATTENDANCE_NOT_FOUND);

        // 신청 취소 훅 → CANCELED_EARLY, 이후 운영자 변경 불가
        UUID cancelledApplicationId = members(table).get(1).getApplication().getId();
        diningAttendanceService.cancelAttendance(cancelledApplicationId);
        assertThat(attendanceRepository.findById(attendances.get(1).getId()).orElseThrow().getStatus())
                .isEqualTo(AttendanceStatus.CANCELED_EARLY);
        assertError(() -> diningAttendanceService.changeAttendanceStatus(
                attendances.get(1).getId(), AttendanceStatus.NO_SHOW, adminId), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    // 2~4인 테이블, 최소 점수 0(답변이 없어 점수는 0), 유예 0분
    private GatheringSession session(LocalDateTime startAt, LocalDateTime endAt) {
        LocalDateTime start = startAt.truncatedTo(ChronoUnit.MINUTES);
        GatheringSession s = GatheringSession.builder().gathering(gathering).eventDate(start.toLocalDate())
                .startTime(start.toLocalTime())
                .endTime(endAt != null ? endAt.truncatedTo(ChronoUnit.MINUTES).toLocalTime() : null)
                .maxAttendees(12).build();
        s.changeMatchingRules(null, 0, 2, 4, BigDecimal.ZERO, null);
        return gatheringSessionRepository.save(s);
    }

    private User user() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder().email(key + "@example.com").password("encoded").name("회원" + key)
                .gender(Gender.MALE).age(30).nickname("회원" + key).build();
        user.approveRandomTable();
        return userRepository.save(user);
    }

    // 제안 테이블을 만들고 확정 파이프라인(즉시 확정)으로 확정한다. 채팅방·참석(SCHEDULED)이 함께 생긴다.
    private DiningTable confirmedTable(GatheringSession session, int memberCount) {
        DiningTable table = diningTableRepository.save(DiningTable.builder().session(session).eventDate(session.getEventDate())
                .groupSize(memberCount).algorithmVersion(MatchingEngine.ALGORITHM_VERSION)
                .confirmAt(LocalDateTime.now().plusHours(1)).build());
        IntStream.rangeClosed(1, memberCount).forEach(seat -> {
            User user = user();
            Application application = Application.builder().bookingNumber("WH-" + user.getNickname()).gathering(gathering)
                    .user(user).name(user.getName()).phone("01012345678").build();
            application.confirm();
            application.changeMatchStatus(MatchStatus.CONFIRM_PENDING);
            application = applicationRepository.save(application);
            applicationCandidateSessionRepository.save(new ApplicationCandidateSession(application, session, 1));
            diningTableMemberRepository.save(DiningTableMember.builder().application(application)
                    .table(table).seatOrder(seat).assignReason(AssignReason.INITIAL).build());
        });
        diningConfirmService.confirmNow(table.getId());
        return diningTableRepository.findById(table.getId()).orElseThrow();
    }

    private List<DiningTableMember> members(DiningTable table) {
        return diningTableMemberRepository.findByTableIdWithApplication(table.getId());
    }

    private UUID memberUserId(DiningTable table, int index) {
        return members(table).get(index).getApplication().getUser().getId();
    }

    // 좌석 순
    private List<Attendance> attendances(DiningTable table) {
        return members(table).stream()
                .map(member -> attendanceRepository.findByTableMemberId(member.getId()).orElseThrow())
                .toList();
    }

    private List<Notification> notifications(UUID userId, NotificationType type) {
        return notificationRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getType() == type)
                .toList();
    }

    private List<String> roomNotices(DiningTable table) {
        assertThat(table.getChatRoomId()).isNotNull();
        return chatMessageRepository.findAll().stream()
                .filter(message -> table.getChatRoomId().equals(message.getRoomId()) && message.getSystemParams() != null)
                .map(ChatMessage::getSystemParams)
                .map(params -> String.valueOf(params.get("text")))
                .toList();
    }

    private static void assertError(ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
