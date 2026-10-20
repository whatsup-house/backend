package com.whatsuphouse.backend.domain.ticket.entity;

import com.whatsuphouse.backend.domain.ticket.enums.TicketProductKind;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // PERIOD(멤버십)는 session_count 0, period_months개월 동안 주 1회 참여. (KAN-401)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketProductKind kind = TicketProductKind.COUNT;

    @Column(name = "period_months")
    private Integer periodMonths;

    public TicketProductOption(String name, int sessionCount, int price) {
        update(name, sessionCount, price);
    }

    public TicketProductOption(String name, int sessionCount, int price, TicketProductKind kind, Integer periodMonths) {
        this(name, sessionCount, price);
        this.kind = kind;
        this.periodMonths = periodMonths;
    }

    public void update(String name, int sessionCount, int price) {
        this.name = name;
        this.sessionCount = sessionCount;
        this.price = price;
    }
}
