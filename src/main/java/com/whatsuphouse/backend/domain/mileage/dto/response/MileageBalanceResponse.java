package com.whatsuphouse.backend.domain.mileage.dto.response;

import com.whatsuphouse.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class MileageBalanceResponse {

    @Schema(description = "회원 ID")
    private UUID userId;

    @Schema(description = "현재 마일리지 잔액", example = "2500")
    private Integer mileage;

    public static MileageBalanceResponse from(User user) {
        return MileageBalanceResponse.builder()
                .userId(user.getId())
                .mileage(user.getMileageBalance())
                .build();
    }
}
