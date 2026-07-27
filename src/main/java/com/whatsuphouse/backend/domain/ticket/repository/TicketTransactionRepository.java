package com.whatsuphouse.backend.domain.ticket.repository;

import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface TicketTransactionRepository extends JpaRepository<TicketTransaction, UUID> {
    List<TicketTransaction> findByTicketPass_IdOrderByCreatedAtDesc(UUID ticketPassId);
    boolean existsByApplication_IdAndTransactionType(UUID applicationId,
                                                      com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType type);
    Optional<TicketTransaction> findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(
            UUID applicationId, com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType type);
}
