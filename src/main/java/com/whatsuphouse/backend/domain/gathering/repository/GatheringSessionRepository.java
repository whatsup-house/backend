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

    // 정원 체크-확정을 직렬화하기 위한 비관적 락. 신청/승인 시 count→save 사이 race를 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GatheringSession s where s.id = :id and s.deletedAt is null")
    Optional<GatheringSession> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);
}
