package com.whatsuphouse.backend.domain.auth.dto.response;

import com.whatsuphouse.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.whatsuphouse.backend.domain.mileage.service.MileageService.SIGNUP_REWARD_AMOUNT;

@Getter
@Builder
public class RegisterResponse {

    private UUID id;
    private String email;
    private String nickname;
    private Integer mileageRewarded;
    private Integer mileageBalance;
    private LocalDateTime createdAt;

    public static RegisterResponse from(User user) {
        return RegisterResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .mileageRewarded(SIGNUP_REWARD_AMOUNT)
                .mileageBalance(user.getMileageBalance())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
