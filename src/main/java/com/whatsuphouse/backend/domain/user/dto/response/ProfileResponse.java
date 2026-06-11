package com.whatsuphouse.backend.domain.user.dto.response;

import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
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
    private String name;
    private Integer age;
    private String nickname;
    private String phone;
    private Gender gender;
    private String instagramId;
    private Mbti mbti;
    private String job;
    private String intro;
    private boolean isAdmin;
    private Integer mileage;
    private LocalDateTime createdAt;

    public static ProfileResponse from(User user) {
        return ProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .age(user.getAge())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .gender(user.getGender())
                .instagramId(user.getInstagramId())
                .mbti(user.getMbti())
                .job(user.getJob())
                .intro(user.getIntro())
                .isAdmin(user.isAdmin())
                .mileage(user.getMileageBalance())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
