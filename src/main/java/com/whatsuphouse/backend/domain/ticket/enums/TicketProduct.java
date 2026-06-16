package com.whatsuphouse.backend.domain.ticket.enums;

import lombok.Getter;

/**
 * 우연한 식탁 이용권 상품. (KAN-261)
 * price는 임시 기본값이며, 확정 후 조정한다.
 */
@Getter
public enum TicketProduct {
    RANDOM_TABLE_FOUR("우연한 식탁 4회권", 4, 40000);

    private final String label;
    private final int sessionCount;
    private final int price;

    TicketProduct(String label, int sessionCount, int price) {
        this.label = label;
        this.sessionCount = sessionCount;
        this.price = price;
    }
}
