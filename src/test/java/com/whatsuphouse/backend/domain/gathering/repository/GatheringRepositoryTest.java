package com.whatsuphouse.backend.domain.gathering.repository;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class GatheringRepositoryTest {

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
    @DisplayName("삭제된 게더링은 목록 조회에서 제외")
    void findByDeletedAtIsNull_excludesDeleted() {
        saveGathering("재즈 게더링", eventDate);
        Gathering deleted = saveGathering("삭제된 게더링", eventDate);
        deleted.delete();
        em.flush();
        em.clear();

        List<Gathering> result = gatheringRepository.findByDeletedAtIsNull();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("재즈 게더링");
    }

    @Test
    @DisplayName("날짜로 게더링 조회")
    void findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc() {
        saveGathering("오늘 게더링", eventDate);
        saveGathering("내일 게더링", eventDate.plusDays(1));
        em.flush();
        em.clear();

        List<Gathering> result = gatheringRepository.findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(
                eventDate);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("오늘 게더링");
    }

    @Test
    @DisplayName("날짜로 게더링 조회 시 시작 시간 오름차순으로 정렬")
    void findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc_sortsByStartTime() {
        saveGathering("퇴근 게더링", eventDate, LocalTime.of(19, 30));
        saveGathering("이른 저녁 게더링", eventDate, LocalTime.of(18, 30));
        em.flush();
        em.clear();

        List<Gathering> result = gatheringRepository.findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(
                eventDate);

        assertThat(result).extracting(Gathering::getTitle)
                .containsExactly("이른 저녁 게더링", "퇴근 게더링");
    }

    @Test
    @DisplayName("상태로 게더링 조회")
    void findByStatusAndDeletedAtIsNull() {
        saveGathering("모집중 게더링", eventDate);
        Gathering closed = saveGathering("마감된 게더링", eventDate);
        closed.changeStatus(GatheringStatus.CLOSED);
        em.flush();
        em.clear();

        List<Gathering> result = gatheringRepository.findByStatusAndDeletedAtIsNull(GatheringStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("모집중 게더링");
    }

    @Test
    @DisplayName("날짜와 상태로 게더링 복합 조회")
    void findByEventDateAndStatusAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc() {
        saveGathering("대상 게더링", eventDate);
        saveGathering("다른 날짜 게더링", eventDate.plusDays(1));
        em.flush();
        em.clear();

        List<Gathering> result = gatheringRepository
                .findByEventDateAndStatusAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(
                        eventDate, GatheringStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("대상 게더링");
    }

    @Test
    @DisplayName("삭제된 게더링 단건 조회 시 빈 Optional 반환")
    void findByIdAndDeletedAtIsNull_excludesDeleted() {
        Gathering gathering = saveGathering("삭제될 게더링", eventDate);
        UUID id = gathering.getId();
        gathering.delete();
        em.flush();
        em.clear();

        Optional<Gathering> result = gatheringRepository.findByIdAndDeletedAtIsNull(id);

        assertThat(result).isEmpty();
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private Gathering saveGathering(String title, LocalDate date) {
        return saveGathering(title, date, null);
    }

    private Gathering saveGathering(String title, LocalDate date, LocalTime startTime) {
        return gatheringRepository.save(Gathering.builder()
                .title(title)
                .eventDate(date)
                .startTime(startTime)
                .maxAttendees(10)
                .build());
    }
}
