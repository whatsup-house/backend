package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.MatchingGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchingGroupRepository extends JpaRepository<MatchingGroup, UUID> {
}
