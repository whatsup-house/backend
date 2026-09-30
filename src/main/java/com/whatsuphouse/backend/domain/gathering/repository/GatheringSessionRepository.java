package com.whatsuphouse.backend.domain.gathering.repository;

import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatheringSessionRepository extends JpaRepository<GatheringSession, UUID> {

    @EntityGraph(attributePaths = {"gathering", "location"})
    List<GatheringSession> findByDeletedAtIsNull();

    @EntityGraph(attributePaths = {"gathering", "location"})
    List<GatheringSession> findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(LocalDate eventDate);

    @EntityGraph(attributePaths = {"gathering", "location"})
    List<GatheringSession> findByEventDateGreaterThanEqualAndDeletedAtIsNull(LocalDate from);

    @EntityGraph(attributePaths = {"gathering", "location"})
    List<GatheringSession> findByStatusAndDeletedAtIsNull(GatheringSessionStatus status);

    @EntityGraph(attributePaths = {"gathering", "location"})
    List<GatheringSession> findByEventDateAndStatusAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(
            LocalDate eventDate, GatheringSessionStatus status);

    @EntityGraph(attributePaths = {"gathering", "location"})
    List<GatheringSession> findByEventDateBetweenAndDeletedAtIsNull(LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"gathering", "location"})
    List<GatheringSession> findByEventDateBetweenAndStatusAndDeletedAtIsNull(
            LocalDate from, LocalDate to, GatheringSessionStatus status);

    @EntityGraph(attributePaths = {"gathering", "location"})
    Optional<GatheringSession> findByIdAndDeletedAtIsNull(UUID id);

    @EntityGraph(attributePaths = {"gathering", "location"})
    List<GatheringSession> findByGathering_IdInAndDeletedAtIsNull(Collection<UUID> gatheringIds);

    /**
     * 매칭 시각이 된 모집 중(OPEN) 우연한 식탁 회차(KAN-346 매칭 스케줄러). 행을 잠그고, 다른 인스턴스가 이미 잠근 행은
     * SKIP LOCKED로 건너뛴다. 잠근 트랜잭션이 CLOSED로 바꿔 커밋하므로 같은 회차를 두 번 잡지 않는다.
     * 네이티브 쿼리인 이유: JPQL에는 SKIP LOCKED가 없다. 행사일이 지난 회차는 잡지 않는다.
     * ID를 문자열로 돌려주는 이유: 네이티브 UUID 스칼라는 DB마다 타입이 달라진다(H2는 byte[]).
     */
    @Query(value = """
            SELECT CAST(s.id AS VARCHAR(36)) FROM gathering_sessions s
            WHERE s.status = 'OPEN' AND s.match_run_at <= :now AND s.event_date >= :today AND s.deleted_at IS NULL
              AND s.gathering_id IN (SELECT g.id FROM gatherings g WHERE g.gathering_type = 'RANDOM_TABLE')
            ORDER BY s.match_run_at
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<String> findDueRandomTableSessionIdsForUpdate(@Param("now") LocalDateTime now, @Param("today") LocalDate today);

    // 정원 체크-확정을 직렬화하기 위한 비관적 락. 신청/승인 시 count→save 사이 race를 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GatheringSession s where s.id = :id and s.deletedAt is null")
    Optional<GatheringSession> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);
}
