package com.whatsuphouse.backend.domain.ticket.entity;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketTransaction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_pass_id", nullable = false)
    private TicketPass ticketPass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TicketTransactionType transactionType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(length = 255)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static TicketTransaction of(TicketPass pass, Application application,
                                       TicketTransactionType type, int quantity, String reason) {
        TicketTransaction transaction = new TicketTransaction();
        transaction.ticketPass = pass;
        transaction.application = application;
        transaction.transactionType = type;
        transaction.quantity = quantity;
        transaction.balanceAfter = pass.getRemainingCount();
        transaction.reason = reason;
        return transaction;
    }
}
