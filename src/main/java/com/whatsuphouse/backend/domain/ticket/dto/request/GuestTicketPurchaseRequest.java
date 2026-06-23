package com.whatsuphouse.backend.domain.ticket.dto.request;

import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestTicketPurchaseRequest {

    @NotBlank(message = "예약번호를 입력해주세요.")
    private String bookingNumber;

    private UUID productId;

    private TicketProduct product;
}
