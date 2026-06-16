package com.whatsuphouse.backend.domain.ticket.dto.request;

import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketPurchaseRequest {

    @Schema(example = "RANDOM_TABLE_FOUR", description = "구매할 이용권 상품")
    @NotNull(message = "구매할 이용권 상품을 선택해주세요.")
    private TicketProduct product;
}
