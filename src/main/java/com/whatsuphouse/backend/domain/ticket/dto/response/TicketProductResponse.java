package com.whatsuphouse.backend.domain.ticket.dto.response;

import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProductKind;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class TicketProductResponse {

    private UUID id;
    private String name;
    private int sessionCount;
    private int price;
    private TicketProductKind kind;
    private Integer periodMonths;

    public static TicketProductResponse from(TicketProductOption product) {
        return TicketProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .sessionCount(product.getSessionCount())
                .price(product.getPrice())
                .kind(product.getKind())
                .periodMonths(product.getPeriodMonths())
                .build();
    }
}
