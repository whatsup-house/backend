package com.whatsuphouse.backend.domain.dining.repository;

import com.whatsuphouse.backend.domain.dining.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    List<Venue> findAllByDeletedAtIsNullOrderByRegionAscNameAsc();

    Optional<Venue> findByIdAndDeletedAtIsNull(UUID id);

    // 회차 풀 자동 추가(KAN-431). 회차 풀 조회와 같은 venue id 순.
    List<Venue> findAllByRegionAndDeletedAtIsNullOrderByIdAsc(String region);
}
