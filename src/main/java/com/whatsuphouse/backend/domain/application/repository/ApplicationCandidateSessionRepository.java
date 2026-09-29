package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplicationCandidateSessionRepository extends JpaRepository<ApplicationCandidateSession, UUID> {
}
