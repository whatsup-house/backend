package com.whatsuphouse.backend.domain.ticket.dto.response;

import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class TicketPassResponse {

    private UUID id;
    private TicketProduct product;
    private String productLabel;
    private int totalCount;
    private int remainingCount;
    private TicketPassStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;

    public static TicketPassResponse from(TicketPass pass) {
        return TicketPassResponse.builder()
                .id(pass.getId())
                .product(pass.getProduct())
                .productLabel(pass.getProduct().getLabel())
                .totalCount(pass.getTotalCount())
                .remainingCount(pass.getRemainingCount())
                .status(pass.getStatus())
                .createdAt(pass.getCreatedAt())
                .activatedAt(pass.getActivatedAt())
                .build();
    }
}
