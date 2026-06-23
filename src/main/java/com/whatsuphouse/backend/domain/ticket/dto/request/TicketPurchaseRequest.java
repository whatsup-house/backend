package com.whatsuphouse.backend.domain.ticket.dto.request;

import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketPurchaseRequest {

    @Schema(example = "RANDOM_TABLE_FOUR", description = "구매할 이용권 상품")
    @NotNull(message = "구매할 이용권 상품을 선택해주세요.")
    private TicketProduct product;

    @Schema(example = "00000000-0000-0000-0000-000000000000", description = "우연한 식탁 신청 후 결제로 이어진 경우 대상 신청 ID")
    private UUID applicationId;
}
