package com.whatsuphouse.backend.domain.gathering.repository;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class GatheringSessionRepositoryTest {

    @Autowired
    private GatheringSessionRepository gatheringSessionRepository;

    @Autowired
    private GatheringRepository gatheringRepository;

    @Autowired
    private TestEntityManager em;

    private LocalDate eventDate;

    @BeforeEach
    void setUp() {
        eventDate = LocalDate.now().plusDays(7);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("삭제된 회차는 목록 조회에서 제외")
    void findByDeletedAtIsNull_excludesDeleted() {
        saveSession("재즈 게더링", eventDate);
        GatheringSession deleted = saveSession("삭제된 게더링", eventDate);
        deleted.delete();
        em.flush();
        em.clear();

        List<GatheringSession> result = gatheringSessionRepository.findByDeletedAtIsNull();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGathering().getTitle()).isEqualTo("재즈 게더링");
    }

    @Test
    @DisplayName("날짜로 회차 조회")
    void findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc() {
        saveSession("오늘 게더링", eventDate);
        saveSession("내일 게더링", eventDate.plusDays(1));
        em.flush();
        em.clear();

        List<GatheringSession> result = gatheringSessionRepository
                .findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(eventDate);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGathering().getTitle()).isEqualTo("오늘 게더링");
    }

    @Test
    @DisplayName("날짜로 회차 조회 시 시작 시간 오름차순으로 정렬")
    void findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc_sortsByStartTime() {
        saveSession("퇴근 게더링", eventDate, LocalTime.of(19, 30));
        saveSession("이른 저녁 게더링", eventDate, LocalTime.of(18, 30));
        em.flush();
        em.clear();

        List<GatheringSession> result = gatheringSessionRepository
                .findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(eventDate);

        assertThat(result).extracting(session -> session.getGathering().getTitle())
                .containsExactly("이른 저녁 게더링", "퇴근 게더링");
    }

    @Test
    @DisplayName("상태로 회차 조회")
    void findByStatusAndDeletedAtIsNull() {
        saveSession("모집중 게더링", eventDate);
        GatheringSession closed = saveSession("마감된 게더링", eventDate);
        closed.changeStatus(GatheringSessionStatus.CLOSED);
        em.flush();
        em.clear();

        List<GatheringSession> result = gatheringSessionRepository.findByStatusAndDeletedAtIsNull(GatheringSessionStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGathering().getTitle()).isEqualTo("모집중 게더링");
    }

    @Test
    @DisplayName("날짜와 상태로 회차 복합 조회")
    void findByEventDateAndStatusAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc() {
        saveSession("대상 게더링", eventDate);
        saveSession("다른 날짜 게더링", eventDate.plusDays(1));
        em.flush();
        em.clear();

        List<GatheringSession> result = gatheringSessionRepository
                .findByEventDateAndStatusAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(
                        eventDate, GatheringSessionStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGathering().getTitle()).isEqualTo("대상 게더링");
    }

    @Test
    @DisplayName("삭제된 회차 단건 조회 시 빈 Optional 반환")
    void findByIdAndDeletedAtIsNull_excludesDeleted() {
        GatheringSession session = saveSession("삭제될 게더링", eventDate);
        UUID id = session.getId();
        session.delete();
        em.flush();
        em.clear();

        Optional<GatheringSession> result = gatheringSessionRepository.findByIdAndDeletedAtIsNull(id);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("종류 ID로 그 종류의 회차만 조회")
    void findByGathering_IdInAndDeletedAtIsNull_returnsSessionsOfGathering() {
        GatheringSession first = saveSession("퇴근 게더링", eventDate);
        Gathering kind = first.getGathering();
        gatheringSessionRepository.save(GatheringSession.builder()
                .gathering(kind).eventDate(eventDate.plusDays(7)).maxAttendees(10).build());
        saveSession("다른 게더링", eventDate);
        em.flush();
        em.clear();

        List<GatheringSession> result = gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(
                List.of(kind.getId()));

        assertThat(result).hasSize(2)
                .allMatch(session -> session.getGathering().getId().equals(kind.getId()));
    }

    // SKIP LOCKED 자체(다른 트랜잭션이 잠근 행 건너뛰기)는 단일 연결 테스트로 재현하지 않는다. 여기서는 네이티브 쿼리의 조건을 확인한다.
    @Test
    @DisplayName("매칭 시각이 된 모집 중 우연한 식탁 회차만 잠금 조회한다")
    void findDueRandomTableSessionIdsForUpdate_returnsOnlyDueOpenRandomTableSessions() {
        LocalDateTime now = LocalDateTime.now();
        Gathering randomTable = gatheringRepository.save(
                Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
        GatheringSession due = saveRandomTableSession(randomTable, eventDate, now.minusMinutes(1));
        saveRandomTableSession(randomTable, eventDate, now.plusMinutes(10));      // 매칭 시각 전
        saveRandomTableSession(randomTable, eventDate, null);                     // 매칭 시각 없음
        saveRandomTableSession(randomTable, LocalDate.now().minusDays(1), now.minusDays(2)); // 행사일 지남
        saveRandomTableSession(randomTable, eventDate, now.minusMinutes(1)).changeStatus(GatheringSessionStatus.CLOSED);
        GatheringSession regular = saveSession("일반 게더링", eventDate);
        ReflectionTestUtils.setField(regular, "matchRunAt", now.minusMinutes(1)); // 일반 모임은 대상 아님
        em.flush();
        em.clear();

        List<String> result = gatheringSessionRepository.findDueRandomTableSessionIdsForUpdate(now, now.toLocalDate());

        assertThat(result).containsExactly(due.getId().toString());
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private GatheringSession saveRandomTableSession(Gathering gathering, LocalDate date, LocalDateTime matchRunAt) {
        GatheringSession session = GatheringSession.builder().gathering(gathering).eventDate(date).maxAttendees(12).build();
        session.changeMatchingRules(matchRunAt, null, null, null, null, null);
        return gatheringSessionRepository.save(session);
    }

    private GatheringSession saveSession(String title, LocalDate date) {
        return saveSession(title, date, null);
    }

    private GatheringSession saveSession(String title, LocalDate date, LocalTime startTime) {
        Gathering gathering = gatheringRepository.save(Gathering.builder().title(title).build());
        return gatheringSessionRepository.save(GatheringSession.builder()
                .gathering(gathering)
                .eventDate(date)
                .startTime(startTime)
                .maxAttendees(10)
                .build());
    }
}
