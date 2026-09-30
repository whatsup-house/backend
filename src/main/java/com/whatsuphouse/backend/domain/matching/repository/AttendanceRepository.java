package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.Attendance;
import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    Optional<Attendance> findByTableMemberId(UUID tableMemberId);

    List<Attendance> findByTableMemberIdIn(Collection<UUID> tableMemberIds);

    interface MemberStatusProjection {
        UUID getTableMemberId();
        AttendanceStatus getStatus();
    }

    interface AttendeeLocationProjection {
        UUID getUserId();
        UUID getLocationId();
    }

    // 테이블 멤버 ID → 참석 상태. 참석 행이 없는 멤버는 결과에 없다.
    // 참가 이력 응답(DiningHistoryResponse.attendanceStatus)용. (KAN-350)
    @Query("select a.tableMemberId as tableMemberId, a.status as status from Attendance a where a.tableMemberId in :tableMemberIds")
    List<MemberStatusProjection> findStatusesByTableMemberIdIn(@Param("tableMemberIds") Collection<UUID> tableMemberIds);

    // since(포함) 이후 회차에 status로 참석한 회원과 그 회차 장소(없으면 NULL). 다음 모집 알림 대상. (KAN-349)
    @Query("""
            select distinct u.id as userId, l.id as locationId
            from DiningTableMember m join m.application ap join ap.user u join m.table t join t.session s
                 left join s.location l, Attendance a
            where a.tableMemberId = m.id and a.status = :status and s.eventDate >= :since
            """)
    List<AttendeeLocationProjection> findAttendeeLocationsSince(@Param("status") AttendanceStatus status,
                                                               @Param("since") LocalDate since);
}
