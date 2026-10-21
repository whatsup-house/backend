package com.whatsuphouse.backend.domain.ticket.repository;

import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface TicketTransactionRepository extends JpaRepository<TicketTransaction, UUID> {
    List<TicketTransaction> findByTicketPass_IdOrderByCreatedAtDesc(UUID ticketPassId);
    boolean existsByApplication_IdAndTransactionType(UUID applicationId,
                                                      com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType type);
    Optional<TicketTransaction> findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(
            UUID applicationId, com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType type);

    List<TicketTransaction> findByApplication_IdInAndTransactionType(
            Collection<UUID> applicationIds, com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType type);

    @Modifying(flushAutomatically = true)
    @Query("delete from TicketTransaction t where t.ticketPass.id in :ticketPassIds")
    void deleteByTicketPassIdIn(@Param("ticketPassIds") Collection<UUID> ticketPassIds);

    // 거래는 이용권 원장이라 남기고 신청 연결만 끊는다. (KAN-442)
    @Modifying(flushAutomatically = true)
    @Query("update TicketTransaction t set t.application = null where t.application.id in :applicationIds")
    void unlinkApplications(@Param("applicationIds") Collection<UUID> applicationIds);

    long countByTransactionTypeAndStatus(com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType type,
                                         com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus status);
}
