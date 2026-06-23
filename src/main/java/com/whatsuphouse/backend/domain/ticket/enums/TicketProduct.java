package com.whatsuphouse.backend.domain.ticket.enums;

import lombok.Getter;

@Getter
public enum TicketProduct {
    RANDOM_TABLE_ONE("우연한 식탁 1회권", 1, 8000),
    RANDOM_TABLE_FOUR("우연한 식탁 4회권", 4, 18000);

    private final String label;
    private final int sessionCount;
    private final int price;

    TicketProduct(String label, int sessionCount, int price) {
        this.label = label;
        this.sessionCount = sessionCount;
        this.price = price;
    }
}
