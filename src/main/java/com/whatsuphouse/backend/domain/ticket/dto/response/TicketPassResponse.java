package com.whatsuphouse.backend.domain.ticket.dto.response;

import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class TicketPassResponse {

    private UUID id;
    private UUID applicationId;
    private UUID productId;
    private String productLabel;
    private int totalCount;
    private int remainingCount;
    private TicketPassStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;
    private int purchaseAmount;
    private LocalDateTime paymentDeadline;
    private LocalDateTime paymentConfirmedAt;

    public static TicketPassResponse from(TicketPass pass) {
        return TicketPassResponse.builder()
                .id(pass.getId())
                .applicationId(pass.getApplication() != null ? pass.getApplication().getId() : null)
                .productId(pass.getProductOption() != null ? pass.getProductOption().getId() : null)
                .productLabel(pass.getProductLabel())
                .totalCount(pass.getTotalCount())
                .remainingCount(pass.getRemainingCount())
                .status(pass.getStatus())
                .createdAt(pass.getCreatedAt())
                .activatedAt(pass.getActivatedAt())
                .purchaseAmount(pass.getPurchaseAmount())
                .paymentDeadline(pass.getPaymentDeadline())
                .paymentConfirmedAt(pass.getPaymentConfirmedAt())
                .build();
    }
}
