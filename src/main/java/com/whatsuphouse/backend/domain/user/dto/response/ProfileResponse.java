package com.whatsuphouse.backend.domain.user.dto.response;

import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.enums.Job;
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
    private String instagramId;
    private Mbti mbti;
    private String job;
    private String jobLabel;
    private String jobCategory;
    private String jobCategoryLabel;
    private String characterUrl;
    private String intro;
    private boolean isAdmin;
    private Integer mileage;
    private LocalDateTime createdAt;

    public static ProfileResponse from(User user, String characterUrl) {
        Job job = Job.fromCode(user.getJob()).orElse(null);
        return ProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .age(user.getCurrentAge())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .instagramId(user.getInstagramId())
                .mbti(user.getMbti())
                .job(user.getJob())
                .jobLabel(job != null ? job.getLabel() : user.getJob())
                .jobCategory(job != null ? job.getCategory().name() : null)
                .jobCategoryLabel(job != null ? job.getCategory().getLabel() : null)
                .characterUrl(characterUrl)
                .intro(user.getIntro())
                .isAdmin(user.isAdmin())
                .mileage(user.getMileageBalance())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
