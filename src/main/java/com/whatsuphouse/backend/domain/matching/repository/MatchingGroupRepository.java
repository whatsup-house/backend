package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.MatchingGroup;
import com.whatsuphouse.backend.domain.matching.enums.MatchingGroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchingGroupRepository extends JpaRepository<MatchingGroup, UUID> {

    List<MatchingGroup> findByGathering_IdAndDeletedAtIsNullOrderByEventDateAsc(UUID gatheringId);

    List<MatchingGroup> findByGathering_IdAndStatusAndDeletedAtIsNull(UUID gatheringId, MatchingGroupStatus status);
}
