package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.MatchingMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchingMemberRepository extends JpaRepository<MatchingMember, UUID> {
}
