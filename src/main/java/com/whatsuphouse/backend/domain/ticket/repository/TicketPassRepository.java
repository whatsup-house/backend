package com.whatsuphouse.backend.domain.ticket.repository;

import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketPassRepository extends JpaRepository<TicketPass, UUID> {

    // 이용권 소유자(회원) 기준 조회. participant.user.id로 찾는다. (KAN-276)
    List<TicketPass> findByParticipant_User_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<TicketPass> findByIdAndDeletedAtIsNull(UUID id);

    List<TicketPass> findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(TicketPassStatus status);

    // 우연한 식탁 신청 시 차감 대상(가장 먼저 활성화된 사용 가능 이용권)을 동시성 안전하게 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketPass t " +
            "where t.participant.user.id = :userId and t.status = :status " +
            "and t.remainingCount > 0 and t.deletedAt is null " +
            "order by t.activatedAt asc")
    List<TicketPass> findUsableForUpdate(@Param("userId") UUID userId,
                                         @Param("status") TicketPassStatus status,
                                         Pageable pageable);
}
