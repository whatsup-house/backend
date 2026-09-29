package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface ApplicationCandidateSessionRepository extends JpaRepository<ApplicationCandidateSession, UUID> {

    // 회원이 이 회차들 중 하나라도 희망 회차로 고른 활성 신청이 있는지. (우연한 식탁 중복 신청 검사)
    boolean existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(
            Collection<UUID> sessionIds, UUID userId);

    boolean existsBySession_IdInAndApplication_DeletedAtIsNull(Collection<UUID> sessionIds);
}
