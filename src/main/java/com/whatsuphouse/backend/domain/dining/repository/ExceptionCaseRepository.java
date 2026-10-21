package com.whatsuphouse.backend.domain.dining.repository;

import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExceptionCaseRepository extends JpaRepository<ExceptionCase, UUID> {

    // 심사용 회차 초기화(KAN-442): 이 회차·테이블·신청에 걸린 예외. 신고(safety_reports.exception_case_id)는 호출 측이 먼저 지운다.
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from ExceptionCase e
            where e.sessionId = :sessionId or e.tableId in :tableIds or e.applicationId in :applicationIds
            """)
    void deleteBySessionOrTablesOrApplications(@Param("sessionId") UUID sessionId,
                                               @Param("tableIds") Collection<UUID> tableIds,
                                               @Param("applicationIds") Collection<UUID> applicationIds);

    interface SessionCountProjection {
        UUID getSessionId();
        Long getCount();
    }

    interface TypeCountProjection {
        ExceptionCaseType getType();
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

    // 숨김 종류(심사용) 회차에 걸린 건. 운영 대시보드 집계에서 뺀다. (KAN-407)
    @Query("""
            select count(e) from ExceptionCase e
            where e.status = :status
              and e.sessionId in (select s.id from GatheringSession s where s.gathering.hidden = true)
            """)
    long countByStatusOnHiddenGatherings(@Param("status") ExceptionCaseStatus status);

    // 유형별 건수. 숨김 종류(심사용) 회차에 걸린 건은 뺀다(countByStatus - countByStatusOnHiddenGatherings와 같은 기준). (KAN-437)
    @Query("""
            select e.type as type, count(e) as count from ExceptionCase e
            where e.status = :status
              and (e.sessionId is null
                   or e.sessionId not in (select s.id from GatheringSession s where s.gathering.hidden = true))
            group by e.type
            """)
    List<TypeCountProjection> countByStatusGroupByTypeExcludingHidden(@Param("status") ExceptionCaseStatus status);

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
