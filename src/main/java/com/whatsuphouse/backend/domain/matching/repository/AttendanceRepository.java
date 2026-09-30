package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    Optional<Attendance> findByTableMemberId(UUID tableMemberId);

    List<Attendance> findByTableMemberIdIn(Collection<UUID> tableMemberIds);
}
