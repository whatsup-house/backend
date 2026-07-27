package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByGatheringIdAndUserIdAndDeletedAtIsNull(UUID gatheringId, UUID userId);

    boolean existsByGatheringIdAndPhoneAndDeletedAtIsNull(UUID gatheringId, String phone);

    int countByGatheringIdAndStatusNotAndDeletedAtIsNull(UUID gatheringId, ApplicationStatus status);

    Optional<Application> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Application> findByPhoneAndBookingNumberAndDeletedAtIsNull(String phone, String bookingNumber);

    List<Application> findByUserIdAndDeletedAtIsNull(UUID userId);

    List<Application> findByDeletedAtIsNull();

    List<Application> findByGatheringIdAndDeletedAtIsNull(UUID gatheringId);

    List<Application> findByGatheringIdAndStatusAndDeletedAtIsNull(UUID gatheringId, ApplicationStatus status);

    List<Application> findByStatusAndDeletedAtIsNull(ApplicationStatus status);
}
