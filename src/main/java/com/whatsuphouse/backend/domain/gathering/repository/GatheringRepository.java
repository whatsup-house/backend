package com.whatsuphouse.backend.domain.gathering.repository;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatheringRepository extends JpaRepository<Gathering, UUID> {

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByDeletedAtIsNull();

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByEventDateAndDeletedAtIsNull(LocalDate eventDate);

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByStatusAndDeletedAtIsNull(GatheringStatus status);

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByEventDateAndStatusAndDeletedAtIsNull(LocalDate eventDate, GatheringStatus status);

    @EntityGraph(attributePaths = "location")
    Optional<Gathering> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByEventDateBetweenAndDeletedAtIsNull(LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByEventDateBetweenAndStatusAndDeletedAtIsNull(LocalDate from, LocalDate to, GatheringStatus status);

    @EntityGraph(attributePaths = "location")
    List<Gathering> findByIsCuratedTrueAndDeletedAtIsNullOrderByCuratedRankAsc();

    List<Gathering> findByIdInAndDeletedAtIsNull(List<UUID> ids);
}
