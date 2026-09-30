package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.MatchingGroup;
import com.whatsuphouse.backend.domain.matching.enums.MatchingGroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MatchingGroupRepository extends JpaRepository<MatchingGroup, UUID> {

    interface SessionStatusCountProjection {
        UUID getSessionId();
        MatchingGroupStatus getStatus();
        Long getCount();
    }

    @Query("""
            select g.session.id as sessionId, g.status as status, count(g) as count
            from MatchingGroup g
            where g.session.id in :sessionIds and g.deletedAt is null
            group by g.session.id, g.status
            """)
    List<SessionStatusCountProjection> countBySessionIdsGroupByStatus(@Param("sessionIds") Collection<UUID> sessionIds);

    List<MatchingGroup> findBySession_IdAndDeletedAtIsNullOrderByEventDateAsc(UUID sessionId);

    List<MatchingGroup> findBySession_IdAndStatusAndDeletedAtIsNull(UUID sessionId, MatchingGroupStatus status);
}
