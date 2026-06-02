package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.MatchingGroup;
import com.whatsuphouse.backend.domain.matching.entity.MatchingMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchingMemberRepository extends JpaRepository<MatchingMember, UUID> {

    void deleteByGroupIn(List<MatchingGroup> groups);

    @org.springframework.data.jpa.repository.Query("""
            select m from MatchingMember m
            join fetch m.application a
            where m.group.id in :groupIds
            order by m.seatOrder asc
            """)
    List<MatchingMember> findByGroupIdsWithApplication(@org.springframework.data.repository.query.Param("groupIds") List<UUID> groupIds);
}
