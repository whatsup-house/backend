package com.whatsuphouse.backend.domain.ticket.dto.request;

import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestTicketPurchaseRequest {

    @NotBlank(message = "예약번호를 입력해주세요.")
    private String bookingNumber;

    @NotNull(message = "구매할 이용권 상품을 선택해주세요.")
    private TicketProduct product;
}
