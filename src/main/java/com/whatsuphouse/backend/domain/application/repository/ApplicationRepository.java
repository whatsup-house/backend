package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID>, ApplicationRepositoryCustom {

    interface ApplicationCountProjection {
        UUID getGatheringId();
        ApplicationStatus getStatus();
        Long getCount();
    }

    @Query("""
            SELECT a.gathering.id AS gatheringId, a.status AS status, COUNT(a) AS count
            FROM Application a
            WHERE a.gathering.id IN :gatheringIds AND a.deletedAt IS NULL
            GROUP BY a.gathering.id, a.status
            """)
    List<ApplicationCountProjection> countByGatheringIdsGroupByStatus(@Param("gatheringIds") List<UUID> gatheringIds);

    boolean existsByGatheringIdAndUserIdAndDeletedAtIsNull(UUID gatheringId, UUID userId);

    boolean existsByGatheringIdAndPhoneAndDeletedAtIsNull(UUID gatheringId, String phone);

    int countByGatheringIdAndStatusNotAndDeletedAtIsNull(UUID gatheringId, ApplicationStatus status);

    @EntityGraph(attributePaths = {"gathering", "user"})
    Optional<Application> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Application> findByPhoneAndBookingNumberAndDeletedAtIsNull(String phone, String bookingNumber);

    @EntityGraph(attributePaths = "gathering")
    List<Application> findByUserIdAndDeletedAtIsNull(UUID userId);

    /**
     * 모임 취소 시 알림 대상 신청자 목록 조회 (FR-NTF-06).
     * PENDING/CONFIRMED 상태의 신청만 대상이며, user를 fetch join해
     * 이메일 발송 시 N+1 없이 user.email에 접근할 수 있습니다.
     */
    @Query("""
            SELECT a FROM Application a
            JOIN FETCH a.user
            WHERE a.gathering.id = :gatheringId
              AND a.status IN :statuses
              AND a.deletedAt IS NULL
            """)
    List<Application> findByGatheringIdAndStatusInWithUser(
            @Param("gatheringId") UUID gatheringId,
            @Param("statuses") List<ApplicationStatus> statuses);

    // 자동매칭 대상: 특정 게더링의 특정 상태(CONFIRMED) 신청 (게스트 포함, user fetch 안 함)
    List<Application> findByGatheringIdAndStatusAndDeletedAtIsNull(UUID gatheringId, ApplicationStatus status);

}
