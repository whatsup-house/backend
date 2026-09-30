package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID>, ApplicationRepositoryCustom {

    interface ApplicationSessionCountProjection {
        UUID getSessionId();
        ApplicationStatus getStatus();
        Long getCount();
    }

    @Query("""
            SELECT a.session.id AS sessionId, a.status AS status, COUNT(a) AS count
            FROM Application a
            WHERE a.session.id IN :sessionIds AND a.deletedAt IS NULL
            GROUP BY a.session.id, a.status
            """)
    List<ApplicationSessionCountProjection> countBySessionIdsGroupByStatus(@Param("sessionIds") List<UUID> sessionIds);

    interface UserCountProjection {
        UUID getUserId();
        Long getCount();
    }

    @Query("""
            SELECT a.user.id AS userId, COUNT(a) AS count
            FROM Application a
            WHERE a.user.id IN :userIds AND a.status = :status
              AND a.gathering.gatheringType = :type AND a.deletedAt IS NULL
            GROUP BY a.user.id
            """)
    List<UserCountProjection> countByUserIdsAndStatusAndType(@Param("userIds") Collection<UUID> userIds,
                                                             @Param("status") ApplicationStatus status,
                                                             @Param("type") GatheringType type);

    // 회원이 해당 회차에 이미 신청했는지.
    boolean existsBySession_IdAndUser_IdAndDeletedAtIsNull(UUID sessionId, UUID userId);

    boolean existsBySession_IdInAndDeletedAtIsNull(Collection<UUID> sessionIds);

    boolean existsBySession_IdAndPhoneAndDeletedAtIsNull(UUID sessionId, String phone);

    // 회차 정원을 차지하는 인원: 관리자 승인(CONFIRMED) + 출석(ATTENDED). PENDING/CANCELLED 제외. (KAN-236)
    int countBySession_IdAndStatusInAndDeletedAtIsNull(UUID sessionId, List<ApplicationStatus> statuses);

    // session은 @Async 메일 발송에서 날짜·시간을 읽으므로 함께 로드한다.
    @EntityGraph(attributePaths = {"gathering", "session", "user"})
    Optional<Application> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Application> findByPhoneAndBookingNumberAndDeletedAtIsNull(String phone, String bookingNumber);

    @EntityGraph(attributePaths = {"user", "gathering", "session"})
    Optional<Application> findByBookingNumberAndDeletedAtIsNull(String bookingNumber);

    @EntityGraph(attributePaths = {"gathering", "session"})
    List<Application> findByUser_IdAndDeletedAtIsNull(UUID userId);

    /**
     * 회차 취소 시 알림 대상 신청자 목록 조회 (FR-NTF-06).
     * PENDING/CONFIRMED 상태의 신청만 대상이며, user를 fetch join해
     * 이메일 발송 시 N+1 없이 user.email에 접근할 수 있습니다.
     */
    @Query("""
            SELECT a FROM Application a
            LEFT JOIN FETCH a.user
            WHERE a.session.id = :sessionId
              AND a.status IN :statuses
              AND a.deletedAt IS NULL
            """)
    List<Application> findBySessionIdAndStatusInWithUser(
            @Param("sessionId") UUID sessionId,
            @Param("statuses") List<ApplicationStatus> statuses);

    // 자동매칭 대상: 특정 회차의 특정 상태(CONFIRMED) 신청 (게스트 포함, user fetch 안 함)
    List<Application> findBySession_IdAndStatusAndDeletedAtIsNull(UUID sessionId, ApplicationStatus status);

    Optional<Application> findFirstByUser_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID userId, ApplicationStatus status);

    // 우연한 식탁 내 신청 목록. 배정 회차의 지역(장소)까지 함께 로드한다. (KAN-342)
    @EntityGraph(attributePaths = {"gathering", "session", "session.location"})
    List<Application> findByUser_IdAndGathering_GatheringTypeAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID userId, GatheringType gatheringType);

    // 삭제(취소)된 신청까지 조회한다. 이미 취소된 신청을 404가 아닌 409로 구분할 때만 쓴다. (KAN-342)
    @EntityGraph(attributePaths = {"gathering", "session", "user"})
    Optional<Application> findIncludingDeletedById(UUID id);
}
