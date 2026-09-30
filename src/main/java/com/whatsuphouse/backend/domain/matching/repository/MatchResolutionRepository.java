package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.MatchResolution;
import com.whatsuphouse.backend.domain.matching.enums.ResolutionStatus;
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

public interface MatchResolutionRepository extends JpaRepository<MatchResolution, UUID> {

    // 선택·만료 처리를 직렬화한다(같은 제안에 대한 중복 요청은 먼저 잡은 쪽만 OFFERED를 본다).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MatchResolution r join fetch r.application where r.id = :id")
    Optional<MatchResolution> findByIdForUpdate(@Param("id") UUID id);

    @Query("select r.id from MatchResolution r where r.status = :status and r.respondBy < :now")
    List<UUID> findIdsByStatusAndRespondByBefore(@Param("status") ResolutionStatus status,
                                                 @Param("now") LocalDateTime now);

    List<MatchResolution> findByApplication_IdInAndStatus(Collection<UUID> applicationIds, ResolutionStatus status);
}
