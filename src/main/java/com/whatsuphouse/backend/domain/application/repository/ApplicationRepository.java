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

    Optional<Application> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Application> findByPhoneAndBookingNumberAndDeletedAtIsNull(String phone, String bookingNumber);

    @EntityGraph(attributePaths = "gathering")
    List<Application> findByUserIdAndDeletedAtIsNull(UUID userId);

}
