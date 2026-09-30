package com.whatsuphouse.backend.domain.dining.repository;

import com.whatsuphouse.backend.domain.dining.entity.SafetyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SafetyReportRepository extends JpaRepository<SafetyReport, UUID> {

    Optional<SafetyReport> findByExceptionCaseId(UUID exceptionCaseId);

    long countByTableIdIn(Collection<UUID> tableIds);
}
