package com.whatsuphouse.backend.domain.dining.repository;

import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExceptionCaseRepository extends JpaRepository<ExceptionCase, UUID> {

    interface SessionCountProjection {
        UUID getSessionId();
        Long getCount();
    }

    // type/status가 null이면 그 조건은 걸지 않는다. 최신 건 우선.
    @Query("""
            select e from ExceptionCase e
            where (:type is null or e.type = :type)
              and (:status is null or e.status = :status)
            order by e.createdAt desc
            """)
    List<ExceptionCase> findAllByFilter(@Param("type") ExceptionCaseType type,
                                        @Param("status") ExceptionCaseStatus status);

    long countByStatus(ExceptionCaseStatus status);

    List<ExceptionCase> findAllByTypeAndTableIdAndStatus(ExceptionCaseType type, UUID tableId, ExceptionCaseStatus status);

    @Query("""
            select e.sessionId as sessionId, count(e) as count
            from ExceptionCase e
            where e.sessionId in :sessionIds and e.status = :status
            group by e.sessionId
            """)
    List<SessionCountProjection> countBySessionIdsAndStatus(@Param("sessionIds") Collection<UUID> sessionIds,
                                                            @Param("status") ExceptionCaseStatus status);
}
