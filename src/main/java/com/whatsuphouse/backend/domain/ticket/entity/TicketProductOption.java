package com.whatsuphouse.backend.domain.ticket.entity;

import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "ticket_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketProductOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    @Column(nullable = false)
    private int price;

    public TicketProductOption(String name, int sessionCount, int price) {
        update(name, sessionCount, price);
    }

    public void update(String name, int sessionCount, int price) {
        this.name = name;
        this.sessionCount = sessionCount;
        this.price = price;
    }
}
