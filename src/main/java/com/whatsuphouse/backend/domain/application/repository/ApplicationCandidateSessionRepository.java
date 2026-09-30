package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ApplicationCandidateSessionRepository extends JpaRepository<ApplicationCandidateSession, UUID> {

    // 회원이 이 회차들 중 하나라도 희망 회차로 고른 활성 신청이 있는지. (우연한 식탁 중복 신청 검사)
    boolean existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(
            Collection<UUID> sessionIds, UUID userId);

    boolean existsBySession_IdInAndApplication_DeletedAtIsNull(Collection<UUID> sessionIds);

    // 신청들의 희망 회차(장소 포함), 우선순위 순. (KAN-342)
    @Query("""
            select c from ApplicationCandidateSession c
            join fetch c.session s
            left join fetch s.location
            where c.application.id in :applicationIds
            order by c.priority asc
            """)
    List<ApplicationCandidateSession> findWithSessionByApplicationIds(
            @Param("applicationIds") Collection<UUID> applicationIds);

    @Query("""
            select c from ApplicationCandidateSession c
            join fetch c.session
            where c.application.id in :applicationIds
            order by c.priority asc
            """)
    List<ApplicationCandidateSession> findByApplicationIdsWithSession(@Param("applicationIds") Collection<UUID> applicationIds);
}
