package com.whatsuphouse.backend.domain.ticket.admin.dto.response;

import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Builder
public class TicketTransactionResponse {
    private UUID id;
    private UUID applicationId;
    private TicketTransactionType type;
    private int quantity;
    private int balanceAfter;
    private String reason;
    private LocalDateTime createdAt;

    public static TicketTransactionResponse from(TicketTransaction transaction) {
        return TicketTransactionResponse.builder()
                .id(transaction.getId())
                .applicationId(transaction.getApplication() != null ? transaction.getApplication().getId() : null)
                .type(transaction.getTransactionType())
                .quantity(transaction.getQuantity())
                .balanceAfter(transaction.getBalanceAfter())
                .reason(transaction.getReason())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
