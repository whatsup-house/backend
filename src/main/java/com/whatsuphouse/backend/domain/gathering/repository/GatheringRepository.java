package com.whatsuphouse.backend.domain.gathering.repository;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatheringRepository extends JpaRepository<Gathering, UUID> {

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByDeletedAtIsNull();

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(LocalDate eventDate);

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByStatusAndDeletedAtIsNull(GatheringStatus status);

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByEventDateAndStatusAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(
            LocalDate eventDate, GatheringStatus status);

    @EntityGraph(attributePaths = "location")
    Optional<Gathering> findByIdAndDeletedAtIsNull(UUID id);

    // 정원 체크-확정을 직렬화하기 위한 비관적 락. 신청/승인 시 count→save 사이 race를 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gathering g where g.id = :id and g.deletedAt is null")
    Optional<Gathering> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByEventDateBetweenAndDeletedAtIsNull(LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByEventDateBetweenAndStatusAndDeletedAtIsNull(LocalDate from, LocalDate to, GatheringStatus status);

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByIsCuratedTrueAndDeletedAtIsNullOrderByCuratedRankAsc();

    List<Gathering> findByIdInAndDeletedAtIsNull(List<UUID> ids);
}
