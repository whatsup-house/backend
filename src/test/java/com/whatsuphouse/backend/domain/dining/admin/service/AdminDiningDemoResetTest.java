package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.auth.service.DemoAccountService;
import com.whatsuphouse.backend.domain.chat.repository.ChatMessageRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatRoomRepository;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DemoResetResponse;
import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import com.whatsuphouse.backend.domain.dining.repository.SessionVenueRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.FormRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.enums.MatchRunTrigger;
import com.whatsuphouse.backend.domain.matching.repository.AttendanceRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.matching.repository.MatchRunRepository;
import com.whatsuphouse.backend.domain.matching.repository.NoticeLogRepository;
import com.whatsuphouse.backend.domain.matching.service.DiningConfirmService;
import com.whatsuphouse.backend.domain.matching.service.DiningMatchService;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketProductRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.enums.Job;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 심사용 회차 초기화(KAN-442) 통합 테스트(H2). 실제 흐름(시드 → 매칭 실행 → 즉시 확정)으로 테이블·단체방·참석·안내 이력·예외를 만든 뒤
 * 다시 초기화해 이 회차 데이터만 지워지고 같은 시드가 다시 들어가는지 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
// 답변 저장·조회(application_answers.value)가 H2 예약어 VALUE에 걸리지 않게 이 컨텍스트만 키워드에서 뺀다.
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:demoreset;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE")
class AdminDiningDemoResetTest {

    @Autowired private AdminDiningDemoService adminDiningDemoService;
    @Autowired private DiningMatchService diningMatchService;
    @Autowired private DiningConfirmService diningConfirmService;
    @Autowired private ApplicationService applicationService;
    @Autowired private TicketService ticketService;
    @Autowired private DemoAccountService demoAccountService;
    @Autowired private UserRepository userRepository;
    @Autowired private GatheringRepository gatheringRepository;
    @Autowired private GatheringSessionRepository gatheringSessionRepository;
    @Autowired private FormRepository formRepository;
    @Autowired private FormQuestionRepository formQuestionRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private ApplicationCandidateSessionRepository applicationCandidateSessionRepository;
    @Autowired private TicketProductRepository ticketProductRepository;
    @Autowired private TicketPassRepository ticketPassRepository;
    @Autowired private TicketTransactionRepository ticketTransactionRepository;
    @Autowired private DiningTableRepository diningTableRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private MatchRunRepository matchRunRepository;
    @Autowired private NoticeLogRepository noticeLogRepository;
    @Autowired private ExceptionCaseRepository exceptionCaseRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private SessionVenueRepository sessionVenueRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Gathering hidden;
    private GatheringSession session;
    private GatheringSession otherSession;

    @BeforeEach
    void setUp() {
        User admin = User.builder().email("admin@example.com").password("encoded").name("운영자")
                .gender(Gender.MALE).age(30).nickname("운영자").build();
        ReflectionTestUtils.setField(admin, "isAdmin", true);
        userRepository.save(admin);
        ticketProductRepository.save(new TicketProductOption("우연한 식탁 1회권", 1, 9_900));
        hidden = gathering(GatheringType.RANDOM_TABLE, true);
        session = session(hidden, 1);
        otherSession = session(hidden, 8);
    }

    // 공유 H2(DB_CLOSE_DELAY=-1)에 커밋된 행이 남지 않게 비운다.
    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'", String.class)
                .forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\""));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    @DisplayName("숨김이 아니거나 우연한 식탁이 아닌 모임의 회차는 409이고 그 회차 신청·매칭 예정 시각을 건드리지 않는다")
    void resetSession_notHiddenRandomTable_conflictAndUntouched() {
        // given
        GatheringSession visible = session(gathering(GatheringType.RANDOM_TABLE, false), 1);
        GatheringSession hiddenRegular = session(gathering(GatheringType.REGULAR, true), 1);
        Application visibleApplication = seedApplication(visible, "demo+judge-1@whatsup.house");

        // when & then
        for (GatheringSession target : List.of(visible, hiddenRegular)) {
            assertThatThrownBy(() -> adminDiningDemoService.resetSession(target.getId()))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMO_RESET_NOT_ALLOWED);
        }
        assertThat(applicationRepository.findById(visibleApplication.getId())).isPresent();
        assertThat(ticketPassRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
                visibleApplication.getUser().getId())).hasSize(1);
        assertThat(gatheringSessionRepository.findById(visible.getId()).orElseThrow().getMatchRunAt()).isNotNull();
    }

    @Test
    @DisplayName("시드 → 매칭·확정 뒤 다시 초기화: 이 회차의 테이블·단체방·참석·안내 이력·예외·신청(취소 포함)·전용 이용권만 지우고, "
            + "같은 시드 16명을 결제 완료·매칭 대기로 다시 넣는다. 다른 회차 신청과 회원 계정은 남고, 다시 매칭하면 테이블 4개 모두 0.70 이상")
    void resetSession_afterMatchAndConfirm_clearsOnlyThisSessionAndReseeds() {
        // given: 첫 초기화(시드), 식당 1곳(홍대/합정, 1테이블)
        Venue venue = venueRepository.save(Venue.builder().name("카멜로 연남").address("서울 마포구 연희로1길 57")
                .region(DiningRegion.HONGDAE.getLabel()).isActive(true).build());
        sessionVenueRepository.save(new SessionVenue(session.getId(), venue.getId(), 1));
        DemoResetResponse first = adminDiningDemoService.resetSession(session.getId());
        Set<UUID> seedUserIds = seededUserIds();

        // then: 시드 16명, 지역 8:8, 결제 완료·매칭 대기, 자동 매칭 꺼짐
        assertThat(first.getDeletedApplicationCount()).isZero();
        assertThat(first.getSeededApplicantCount()).isEqualTo(16);
        assertThat(seedUserIds).hasSize(16);
        assertSeeded();
        GatheringSession reloaded = gatheringSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getMatchRunAt()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(GatheringSessionStatus.OPEN);

        // given: 매칭 → 즉시 확정(테이블·참석·식당·단체방·알림·안내 이력·VENUE 예외)
        diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, null);
        List<DiningTable> tables = activeTables(session);
        assertThat(tables).hasSize(4).allSatisfy(t -> assertThat(t.getGroupScore().doubleValue()).isGreaterThanOrEqualTo(0.70));
        tables.forEach(t -> diningConfirmService.confirmNow(t.getId()));
        List<UUID> tableIds = tables.stream().map(DiningTable::getId).toList();
        List<UUID> roomIds = diningTableRepository.findAllById(tableIds).stream().map(DiningTable::getChatRoomId).toList();
        assertThat(roomIds).doesNotContainNull();
        assertThat(attendanceRepository.findAll()).hasSize(16);
        assertThat(sessionVenueRepository.findBySessionIdOrderByVenueIdAsc(session.getId()))
                .singleElement().satisfies(sv -> assertThat(sv.getUsedTables()).isEqualTo(1));
        assertThat(exceptionCaseRepository.findAll()).anyMatch(c -> session.getId().equals(c.getSessionId()));
        // 심사위원 체험 계정의 이 회차 신청(진행 중 1건 + 취소 1건)과 다른 회차 신청
        Application judge = seedApplication(session, "demo+judge-1@whatsup.house");
        Application cancelled = seedApplication(session, "demo+judge-2@whatsup.house");
        transactionTemplate.executeWithoutResult(status ->
                applicationRepository.findById(cancelled.getId()).orElseThrow().cancel());
        Application other = seedApplication(otherSession, "demo+judge-3@whatsup.house");

        // when
        DemoResetResponse second = adminDiningDemoService.resetSession(session.getId());

        // then: 응답·이 회차 데이터 삭제
        assertThat(second.getDeletedApplicationCount()).isEqualTo(18);
        assertThat(second.getDeletedTableCount()).isEqualTo(4);
        assertThat(diningTableRepository.findAllById(tableIds)).isEmpty();
        assertThat(chatRoomRepository.findAllById(roomIds)).isEmpty();
        assertThat(chatMessageRepository.findAll()).noneMatch(m -> roomIds.contains(m.getRoomId()));
        assertThat(attendanceRepository.findAll()).isEmpty();
        assertThat(matchRunRepository.findFirstBySessionIdOrderByStartedAtDesc(session.getId())).isEmpty();
        assertThat(noticeLogRepository.findBySessionIdOrderByCreatedAtDesc(session.getId())).isEmpty();
        assertThat(exceptionCaseRepository.findAll()).noneMatch(c -> session.getId().equals(c.getSessionId()));
        assertThat(sessionVenueRepository.findBySessionIdOrderByVenueIdAsc(session.getId()))
                .singleElement().satisfies(sv -> assertThat(sv.getUsedTables()).isZero());
        // 심사위원 신청·전용 이용권은 지우고 계정은 남긴다
        for (Application removed : List.of(judge, cancelled)) {
            assertThat(applicationRepository.findIncludingDeletedById(removed.getId())).isEmpty();
            assertThat(userRepository.findById(removed.getUser().getId())).isPresent();
            assertThat(ticketPassRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(removed.getUser().getId()))
                    .isEmpty();
        }
        // 다른 회차 신청과 그 이용권 원장은 그대로
        assertThat(applicationRepository.findById(other.getId())).hasValueSatisfying(a ->
                assertThat(a.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED));
        TicketPass otherPass = ticketPassRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
                other.getUser().getId()).get(0);
        assertThat(ticketTransactionRepository.findByTicketPass_IdOrderByCreatedAtDesc(otherPass.getId()))
                .extracting(TicketTransaction::getTransactionType)
                .containsExactlyInAnyOrder(TicketTransactionType.ISSUE, TicketTransactionType.USE);

        // then: 같은 시드 계정으로 다시 16명(멱등)
        assertThat(second.getSeededApplicantCount()).isEqualTo(16);
        assertThat(seededUserIds()).isEqualTo(seedUserIds);
        assertSeeded();

        // then: 이전 확정 테이블이 지워져 "이전 만남" 감점 없이 다시 0.70 이상
        diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, null);
        assertThat(activeTables(session)).hasSize(4)
                .allSatisfy(t -> assertThat(t.getGroupScore().doubleValue()).isGreaterThanOrEqualTo(0.70));
    }

    // 이 회차 신청 = 시드 16명뿐이고, 모두 결제 완료(이용권 1회권 차감)·참가 확정·매칭 대기, 희망 회차 1지망 = 이 회차, 지역 8:8
    private void assertSeeded() {
        List<Application> applications = applicationRepository.findApplications(session.getId(), null);
        assertThat(applications).hasSize(16).allSatisfy(a -> {
            assertThat(a.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
            assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.WAITING);
            assertThat(a.getSession()).isNull();
            assertThat(a.isPaymentConfirmed()).isTrue();
            assertThat(a.getUser().isDemo()).isTrue();
            assertThat(a.getUser().isApprovedForRandomTable()).isTrue();
            assertThat(a.getUser().getEmail()).startsWith("demo+seed-").endsWith("@whatsup.house");
        });
        List<UUID> ids = applications.stream().map(Application::getId).toList();
        assertThat(ticketService.findDeductionStatuses(ids).values()).hasSize(16).containsOnly(TicketDeductionStatus.DEDUCTED);
        assertThat(applicationCandidateSessionRepository.findByApplicationIdsWithSession(ids))
                .hasSize(16).allSatisfy(c -> {
                    assertThat(c.getSession().getId()).isEqualTo(session.getId());
                    assertThat(c.getPriority()).isEqualTo(1);
                });
        Map<DiningRegion, Long> regions = transactionTemplate.execute(status -> applicationRepository.findAllById(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getCandidateRegions().get(0), Collectors.counting())));
        assertThat(regions).containsOnly(Map.entry(DiningRegion.HONGDAE, 8L), Map.entry(DiningRegion.GANGNAM, 8L));
    }

    private Set<UUID> seededUserIds() {
        return applicationRepository.findApplications(session.getId(), null).stream()
                .map(a -> a.getUser().getId()).collect(Collectors.toSet());
    }

    private List<DiningTable> activeTables(GatheringSession target) {
        return diningTableRepository.findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(target.getId(),
                List.of(DiningTableStatus.PROPOSED, DiningTableStatus.CONFIRMED));
    }

    // 결제 완료·매칭 대기 체험 계정 신청(시드와 같은 정상 결제 흐름)
    private Application seedApplication(GatheringSession target, String email) {
        User user = demoAccountService.findOrCreateSeedUser(email, Gender.FEMALE, 1995, Mbti.ENFP, Job.SOFTWARE_DEVELOPER);
        Application application = applicationService.createDemoSeedApplication(target, user, List.of(DiningRegion.HONGDAE),
                Map.of(ReservedQuestionKey.BIRTH_YEAR, 1995, ReservedQuestionKey.GENDER, "FEMALE"));
        ticketService.settleDemoSeedTicket(user, application);
        return application;
    }

    private Gathering gathering(GatheringType type, boolean isHidden) {
        Gathering gathering = Gathering.builder().title("우연한 식탁 (심사용)").gatheringType(type).basePrice(9_900).build();
        ReflectionTestUtils.setField(gathering, "hidden", isHidden);
        gathering = gatheringRepository.save(gathering);
        Form form = formRepository.save(Form.builder().gathering(gathering).isTemplate(false).build());
        int order = 0;
        for (ReservedQuestionKey key : ReservedQuestionKey.values()) {
            QuestionType questionType = switch (key) {
                case BIRTH_YEAR -> QuestionType.NUMBER;
                case GENDER -> QuestionType.SINGLE_CHOICE;
                case MBTI -> QuestionType.MBTI_INPUT;
                case DIET -> QuestionType.SHORT_TEXT;
                default -> QuestionType.MULTI_CHOICE;
            };
            formQuestionRepository.save(FormQuestion.builder().form(form).questionKey(key.name().toLowerCase())
                    .type(questionType).label(key.name()).displayOrder(order++).reservedKey(key).build());
        }
        return gathering;
    }

    private GatheringSession session(Gathering gathering, int daysLater) {
        GatheringSession s = GatheringSession.builder().gathering(gathering).eventDate(LocalDate.now().plusDays(daysLater))
                .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(21, 0)).maxAttendees(20).build();
        if (gathering.getGatheringType() == GatheringType.RANDOM_TABLE) {
            s.changeMatchingRules(LocalDateTime.now().plusDays(daysLater), 120, 4, 6, null, null);
        }
        return gatheringSessionRepository.save(s);
    }
}
