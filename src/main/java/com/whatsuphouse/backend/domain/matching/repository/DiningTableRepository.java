package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiningTableRepository extends JpaRepository<DiningTable, UUID> {

    interface SessionStatusCountProjection {
        UUID getSessionId();
        DiningTableStatus getStatus();
        Long getCount();
    }

    @Query("""
            select t.session.id as sessionId, t.status as status, count(t) as count
            from DiningTable t
            where t.session.id in :sessionIds and t.deletedAt is null
            group by t.session.id, t.status
            """)
    List<SessionStatusCountProjection> countBySessionIdsGroupByStatus(@Param("sessionIds") Collection<UUID> sessionIds);

    List<DiningTable> findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID sessionId, Collection<DiningTableStatus> statuses);

    // 재실행 시 해체 대상: 잠기지 않은 제안 테이블
    List<DiningTable> findBySession_IdAndStatusAndLockedFalseAndDeletedAtIsNull(UUID sessionId, DiningTableStatus status);

    // 수동 조정·확정 후 취소 재조정을 같은 테이블끼리 직렬화한다. (KAN-347)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTable t where t.id = :id and t.deletedAt is null")
    Optional<DiningTable> findByIdForUpdate(@Param("id") UUID id);
}
