package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    // 확정 시각이 지난 테이블(자동 확정 스케줄러). 확정 시각 순.
    @Query("""
            select t.id from DiningTable t
            where t.status = :status and t.confirmAt <= :now and t.deletedAt is null
            order by t.confirmAt asc
            """)
    List<UUID> findIdsByStatusAndConfirmAtBefore(@Param("status") DiningTableStatus status,
                                                 @Param("now") LocalDateTime now);

    // 테이블 행을 읽지 않고 회차 ID만. 회차를 먼저 잠근 뒤 테이블을 새로 읽기 위해 쓴다.
    @Query("select t.session.id from DiningTable t where t.id = :id")
    Optional<UUID> findSessionIdById(@Param("id") UUID id);
}
