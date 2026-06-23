package com.whatsuphouse.backend.domain.ticket.admin.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TicketProductRequest {

    @NotBlank(message = "이용권 이름을 입력해주세요.")
    @Size(max = 100, message = "이용권 이름은 100자 이하여야 합니다.")
    private String name;

    @NotNull(message = "이용 횟수를 입력해주세요.")
    @Min(value = 1, message = "이용 횟수는 1회 이상이어야 합니다.")
    private Integer sessionCount;

    @NotNull(message = "가격을 입력해주세요.")
    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    private Integer price;
}
