package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ApplicationCandidateSessionRepository extends JpaRepository<ApplicationCandidateSession, UUID> {

    // 회원이 이 회차들 중 하나라도 희망 회차로 고른 활성 신청이 있는지. (우연한 식탁 중복 신청 검사)
    boolean existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(
            Collection<UUID> sessionIds, UUID userId);

    boolean existsBySession_IdInAndApplication_DeletedAtIsNull(Collection<UUID> sessionIds);

    // 회원이 [from, to] 날짜 회차에 활성 신청이 있는지. 배정 회차가 있으면 그 회차만, 없으면 희망 회차로 본다. (멤버십 주 1회, KAN-401)
    @Query("""
            select count(c) > 0 from ApplicationCandidateSession c
            join c.application a
            join c.session s
            where a.user.id = :userId and a.deletedAt is null and a.status in :statuses
              and a.gathering.gatheringType = :type
              and s.eventDate between :from and :to
              and (a.session is null or a.session = s)
            """)
    boolean existsActiveApplicationInDateRange(@Param("userId") UUID userId,
                                               @Param("type") GatheringType type,
                                               @Param("statuses") Collection<ApplicationStatus> statuses,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    // 대체 회차 이동(KAN-347) 시 희망 회차를 통째로 교체한다.
    void deleteByApplication_Id(UUID applicationId);

    // 신청들의 희망 회차(장소 포함), 우선순위 순. (KAN-342)
    @Query("""
            select c from ApplicationCandidateSession c
            join fetch c.session s
            left join fetch s.location
            where c.application.id in :applicationIds
            order by c.priority asc
            """)
    List<ApplicationCandidateSession> findByApplicationIdsWithSession(@Param("applicationIds") Collection<UUID> applicationIds);
}
