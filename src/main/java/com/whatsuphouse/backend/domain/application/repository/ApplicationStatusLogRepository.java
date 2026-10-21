package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.ApplicationStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ApplicationStatusLogRepository extends JpaRepository<ApplicationStatusLog, UUID> {

    List<ApplicationStatusLog> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ApplicationStatusLog l where l.applicationId in :applicationIds")
    void deleteByApplicationIdIn(@Param("applicationIds") Collection<UUID> applicationIds);
}
