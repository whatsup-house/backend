package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class ApplicationRepositoryTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationCandidateSessionRepository applicationCandidateSessionRepository;

    @Autowired
    private GatheringRepository gatheringRepository;

    @Autowired
    private GatheringSessionRepository gatheringSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager em;

    private Gathering gathering;
    private GatheringSession session;
    private User user;

    @BeforeEach
    void setUp() {
        gathering = gatheringRepository.save(Gathering.builder()
                .title("재즈 게더링")
                .build());
        session = saveSession(LocalDate.now().plusDays(7));

        user = userRepository.save(User.builder()
                .email("test@example.com")
                .password("encoded")
                .name("김철수")
                .gender(Gender.MALE)
                .age(28)
                .nickname("chulsoo")
                .phone("01012345678")
                .build());


        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("정원 차지 인원(CONFIRMED+ATTENDED)만 카운트하고 PENDING/CANCELLED는 제외 (KAN-236)")
    void countBySessionIdAndStatusInAndDeletedAtIsNull() {
        saveApplication("WH001", session, user, null); // PENDING — 제외
        Application confirmed = saveApplication("WH002", session, null, "01011111111");
        confirmed.confirm(); // CONFIRMED — 포함
        Application attended = saveApplication("WH003", session, null, "01022222222");
        attended.confirm();
        attended.attend(); // ATTENDED — 포함
        Application cancelled = saveApplication("WH004", session, null, "01033333333");
        cancelled.cancel(); // CANCELLED — 제외
        em.flush();
        em.clear();

        int count = applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(
                session.getId(), ApplicationStatus.SEAT_OCCUPYING);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("정원은 회차별로 센다 — 같은 종류의 다른 회차 신청은 제외 (KAN-337)")
    void countBySessionIdAndStatusInAndDeletedAtIsNull_otherSessionExcluded() {
        GatheringSession otherSession = saveSession(LocalDate.now().plusDays(14));
        saveApplication("WH001", session, null, "01011111111").confirm();
        saveApplication("WH002", otherSession, null, "01022222222").confirm();
        em.flush();
        em.clear();

        int count = applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(
                session.getId(), ApplicationStatus.SEAT_OCCUPYING);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("회원의 회차 중복 신청 여부 확인 — 같은 종류의 다른 회차는 중복이 아니다")
    void existsBySessionIdAndUserIdAndDeletedAtIsNull() {
        GatheringSession otherSession = saveSession(LocalDate.now().plusDays(14));
        saveApplication("WH001", session, user, null);
        em.flush();
        em.clear();

        assertThat(applicationRepository.existsBySession_IdAndUser_IdAndDeletedAtIsNull(
                session.getId(), user.getId())).isTrue();
        assertThat(applicationRepository.existsBySession_IdAndUser_IdAndDeletedAtIsNull(
                otherSession.getId(), user.getId())).isFalse();
    }

    @Test
    @DisplayName("비회원의 전화번호 중복 신청 여부 확인")
    void existsBySessionIdAndPhoneAndDeletedAtIsNull() {
        saveApplication("WH001", session, null, "01099999999");
        em.flush();
        em.clear();

        boolean exists = applicationRepository.existsBySession_IdAndPhoneAndDeletedAtIsNull(
                session.getId(), "01099999999");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("전화번호와 예약번호로 신청 단건 조회")
    void findByPhoneAndBookingNumberAndDeletedAtIsNull() {
        saveApplication("WH260428-ABC123", session, null, "01012345678");
        em.flush();
        em.clear();

        Optional<Application> result = applicationRepository.findByPhoneAndBookingNumberAndDeletedAtIsNull(
                "01012345678", "WH260428-ABC123");

        assertThat(result).isPresent();
        assertThat(result.get().getBookingNumber()).isEqualTo("WH260428-ABC123");
        assertThat(result.get().getGathering().getId()).isEqualTo(gathering.getId());
    }

    @Test
    @DisplayName("삭제된 신청은 전화번호+예약번호 조회에서 제외")
    void findByPhoneAndBookingNumberAndDeletedAtIsNull_excludesDeleted() {
        Application application = saveApplication("WH260428-DEL999", session, null, "01077777777");
        application.cancel();
        em.flush();
        em.clear();

        Optional<Application> result = applicationRepository.findByPhoneAndBookingNumberAndDeletedAtIsNull(
                "01077777777", "WH260428-DEL999");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("회원 ID로 삭제되지 않은 신청 목록 조회")
    void findByUserIdAndDeletedAtIsNull() {
        saveApplication("WH001", session, user, null);
        saveApplication("WH002", session, user, null);
        em.flush();
        em.clear();

        List<Application> result = applicationRepository.findByUser_IdAndDeletedAtIsNull(user.getId());

        assertThat(result).hasSize(2);
    }

    // ── countBySessionIdsGroupByStatus() ─────────────────────────────────────

    @Test
    @DisplayName("회차 ID 목록으로 status별 신청 수 집계")
    void countBySessionIdsGroupByStatus_returnsGroupedCount() {
        // GIVEN
        saveApplication("WH101", session, user, null);
        Application app2 = saveApplication("WH102", session, null, "01022222222");
        app2.confirm();
        em.flush();
        em.clear();

        // WHEN
        List<ApplicationRepository.ApplicationSessionCountProjection> result =
                applicationRepository.countBySessionIdsGroupByStatus(List.of(session.getId()));

        // THEN
        assertThat(result).hasSize(2);
        result.forEach(p -> assertThat(p.getSessionId()).isEqualTo(session.getId()));
        long pendingCount = result.stream()
                .filter(p -> p.getStatus() == ApplicationStatus.PENDING)
                .mapToLong(ApplicationRepository.ApplicationSessionCountProjection::getCount)
                .sum();
        long confirmedCount = result.stream()
                .filter(p -> p.getStatus() == ApplicationStatus.CONFIRMED)
                .mapToLong(ApplicationRepository.ApplicationSessionCountProjection::getCount)
                .sum();
        assertThat(pendingCount).isEqualTo(1);
        assertThat(confirmedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("CANCELLED된 신청은 집계에서 제외")
    void countBySessionIdsGroupByStatus_excludesCancelled() {
        // GIVEN
        saveApplication("WH201", session, user, null);
        Application app2 = saveApplication("WH202", session, null, "01033333333");
        app2.cancel();
        em.flush();
        em.clear();

        // WHEN
        List<ApplicationRepository.ApplicationSessionCountProjection> result =
                applicationRepository.countBySessionIdsGroupByStatus(List.of(session.getId()));

        // THEN — CANCELLED는 deletedAt이 설정되어 집계에서 제외됨
        long total = result.stream().mapToLong(ApplicationRepository.ApplicationSessionCountProjection::getCount).sum();
        assertThat(total).isEqualTo(1);
    }

    // ── 우연한 식탁 희망 회차 (KAN-338) ─────────────────────────────────────

    @Test
    @DisplayName("회차별 신청 목록에는 배정 전 우연한 식탁 신청 중 그 회차를 희망한 신청도 포함된다")
    void findApplications_includesUnassignedCandidates() {
        // given
        Gathering randomTable = gatheringRepository.save(Gathering.builder()
                .title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
        GatheringSession week1 = saveSession(randomTable, LocalDate.now().plusDays(7));
        GatheringSession week2 = saveSession(randomTable, LocalDate.now().plusDays(14));
        Application wantsBoth = saveUnassigned("WH-RT-0001", randomTable, week1, week2);
        Application wantsWeek2 = saveUnassigned("WH-RT-0002", randomTable, week2);
        em.flush();
        em.clear();

        // when
        List<Application> week1Applicants = applicationRepository.findApplications(week1.getId(), null);
        List<Application> week2Applicants = applicationRepository.findApplications(week2.getId(), null);

        // then
        assertThat(week1Applicants).extracting(Application::getId).containsExactly(wantsBoth.getId());
        assertThat(week2Applicants).extracting(Application::getId)
                .containsExactlyInAnyOrder(wantsBoth.getId(), wantsWeek2.getId());
    }

    @Test
    @DisplayName("희망 회차 중복 검사·신청 존재 검사는 취소(soft delete)된 신청을 제외한다")
    void candidateExistsQueries_excludeCancelledApplications() {
        // given
        Gathering randomTable = gatheringRepository.save(Gathering.builder()
                .title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
        GatheringSession week1 = saveSession(randomTable, LocalDate.now().plusDays(7));
        GatheringSession week2 = saveSession(randomTable, LocalDate.now().plusDays(14));
        Application active = saveUnassigned("WH-RT-0003", randomTable, week1);
        Application cancelled = saveUnassigned("WH-RT-0004", randomTable, week2);
        cancelled.cancel();
        em.flush();
        em.clear();

        // when & then
        assertThat(applicationCandidateSessionRepository
                .existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(List.of(week1.getId()), user.getId()))
                .isTrue();
        assertThat(applicationCandidateSessionRepository
                .existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(List.of(week2.getId()), user.getId()))
                .isFalse();
        assertThat(applicationCandidateSessionRepository
                .existsBySession_IdInAndApplication_DeletedAtIsNull(List.of(week2.getId()))).isFalse();
        assertThat(applicationRepository.existsBySession_IdInAndDeletedAtIsNull(List.of(week1.getId()))).isFalse();
        assertThat(active.getSession()).isNull();
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private GatheringSession saveSession(Gathering kind, LocalDate eventDate) {
        return gatheringSessionRepository.save(GatheringSession.builder()
                .gathering(kind)
                .eventDate(eventDate)
                .maxAttendees(10)
                .build());
    }

    // 배정 회차 없이 희망 회차만 가진 우연한 식탁 신청(회원).
    private Application saveUnassigned(String bookingNumber, Gathering kind, GatheringSession... candidates) {
        Application saved = applicationRepository.save(Application.builder()
                .bookingNumber(bookingNumber)
                .gathering(kind)
                .user(user)
                .name(user.getName())
                .phone(user.getPhone())
                .build());
        for (int i = 0; i < candidates.length; i++) {
            applicationCandidateSessionRepository.save(new ApplicationCandidateSession(saved, candidates[i], i + 1));
        }
        return saved;
    }

    private GatheringSession saveSession(LocalDate eventDate) {
        return gatheringSessionRepository.save(GatheringSession.builder()
                .gathering(gathering)
                .eventDate(eventDate)
                .maxAttendees(10)
                .build());
    }

    private Application saveApplication(String bookingNumber, GatheringSession s, User u, String phone) {
        String name = (u != null) ? u.getName() : "비회원";
        String phoneValue = (u != null) ? u.getPhone() : phone;

        return applicationRepository.save(Application.builder()
                .bookingNumber(bookingNumber)
                .session(s)
                .user(u)
                .name(name)
                .phone(phoneValue)
                .build());
    }
}
