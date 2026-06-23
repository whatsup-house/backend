package com.whatsuphouse.backend.domain.ticket.repository;

import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketProductRepository extends JpaRepository<TicketProductOption, UUID> {

    List<TicketProductOption> findAllByDeletedAtIsNullOrderBySessionCountAscPriceAscCreatedAtAsc();

    Optional<TicketProductOption> findByIdAndDeletedAtIsNull(UUID id);
}
