package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.MatchingGroup;
import com.whatsuphouse.backend.domain.matching.entity.MatchingMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MatchingMemberRepository extends JpaRepository<MatchingMember, UUID> {

    void deleteByGroupIn(List<MatchingGroup> groups);

    boolean existsByApplication_Id(UUID applicationId);

    int countByGroup_Id(UUID groupId);

    @Query("""
            select m from MatchingMember m
            join fetch m.application a
            where m.group.id = :groupId
            order by m.seatOrder asc
            """)
    List<MatchingMember> findByGroupIdWithApplication(@Param("groupId") UUID groupId);

    @Query("""
            select m from MatchingMember m
            join fetch m.application a
            where m.group.id in :groupIds
            order by m.seatOrder asc
            """)
    List<MatchingMember> findByGroupIdsWithApplication(@Param("groupIds") List<UUID> groupIds);
}
