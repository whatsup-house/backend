package com.whatsuphouse.backend.domain.gathering.repository;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatheringRepository extends JpaRepository<Gathering, UUID> {

    List<Gathering> findByDeletedAtIsNull();

    List<Gathering> findByEventDateAndDeletedAtIsNull(LocalDate eventDate);

    List<Gathering> findByStatusAndDeletedAtIsNull(GatheringStatus status);

    List<Gathering> findByEventDateAndStatusAndDeletedAtIsNull(LocalDate eventDate, GatheringStatus status);

    Optional<Gathering> findByIdAndDeletedAtIsNull(UUID id);
}
