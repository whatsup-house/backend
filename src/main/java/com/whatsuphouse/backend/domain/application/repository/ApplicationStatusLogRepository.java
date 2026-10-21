package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.ApplicationStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationStatusLogRepository extends JpaRepository<ApplicationStatusLog, UUID> {

    List<ApplicationStatusLog> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
