package com.whatsuphouse.backend.domain.user.dto.response;

import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ProfileResponse {

    private UUID id;
    private String email;
    private String nickname;
    private String phone;
    private String instagramId;
    private Mbti mbti;
    private String job;
    private String intro;
    private boolean isAdmin;
    private LocalDateTime createdAt;

    public static ProfileResponse from(User user) {
        return ProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .instagramId(user.getInstagramId())
                .mbti(user.getMbti())
                .job(user.getJob())
                .intro(user.getIntro())
                .isAdmin(user.isAdmin())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
