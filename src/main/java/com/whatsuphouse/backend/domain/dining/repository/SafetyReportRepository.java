package com.whatsuphouse.backend.domain.dining.repository;

import com.whatsuphouse.backend.domain.dining.entity.SafetyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SafetyReportRepository extends JpaRepository<SafetyReport, UUID> {

    Optional<SafetyReport> findByExceptionCaseId(UUID exceptionCaseId);

    long countByTableIdIn(Collection<UUID> tableIds);

    interface UserCountProjection {
        UUID getUserId();
        Long getCount();
    }

    // 회원별 신고된 건수(처리 여부 무관). 신고가 없는 회원은 결과에 없다. (KAN-437)
    @Query("""
            select r.reportedUserId as userId, count(r) as count from SafetyReport r
            where r.reportedUserId in :userIds
            group by r.reportedUserId
            """)
    List<UserCountProjection> countByReportedUserIds(@Param("userIds") Collection<UUID> userIds);
}
