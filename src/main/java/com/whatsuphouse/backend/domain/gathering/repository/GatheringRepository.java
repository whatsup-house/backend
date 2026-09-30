package com.whatsuphouse.backend.domain.gathering.repository;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatheringRepository extends JpaRepository<Gathering, UUID> {

    Optional<Gathering> findByIdAndDeletedAtIsNull(UUID id);

    List<Gathering> findByIsCuratedTrueAndDeletedAtIsNullOrderByCuratedRankAsc();
}
