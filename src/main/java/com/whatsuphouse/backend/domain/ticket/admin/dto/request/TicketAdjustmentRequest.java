package com.whatsuphouse.backend.domain.ticket.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketAdjustmentRequest {
    @NotNull
    private Integer quantity;
    @NotBlank
    private String reason;
}
