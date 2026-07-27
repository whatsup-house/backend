package com.whatsuphouse.backend.domain.user.admin.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.enums.UserAccountStatus;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 관리자 회원 상세 응답. 목록에 없는 프로필/마일리지/신청 이력/계정 상태를 포함한다. (KAN-188)
 */
@Getter
@Builder
public class UserDetailResponse {

    private UUID id;
    private String email;
    private String name;
    private String nickname;
    private String phone;
    private Gender gender;
    private Integer age;
    private String job;
    private Mbti mbti;
    private String intro;
    private String instagramId;
    private boolean isAdmin;
    private Integer mileage;
    private UserAccountStatus accountStatus;
    private long totalApplications;
    private long attendedCount;
    private LocalDateTime createdAt;
    private List<ApplicationHistoryItem> applicationHistory;

    @Getter
    @Builder
    public static class ApplicationHistoryItem {
        private UUID applicationId;
        private String bookingNumber;
        private String gatheringTitle;
        private ApplicationStatus status;
        private LocalDateTime createdAt;
    }

    public static UserDetailResponse from(User user, List<Application> applications) {
        long attended = applications.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.ATTENDED)
                .count();

        List<ApplicationHistoryItem> history = applications.stream()
                .map(a -> ApplicationHistoryItem.builder()
                        .applicationId(a.getId())
                        .bookingNumber(a.getBookingNumber())
                        .gatheringTitle(a.getGathering().getTitle())
                        .status(a.getStatus())
                        .createdAt(a.getCreatedAt())
                        .build())
                .toList();

        return UserDetailResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .gender(user.getGender())
                .age(user.getCurrentAge())
                .job(user.getJob())
                .mbti(user.getMbti())
                .intro(user.getIntro())
                .instagramId(user.getInstagramId())
                .isAdmin(user.isAdmin())
                .mileage(user.getMileageBalance())
                .accountStatus(user.getEffectiveAccountStatus())
                .totalApplications(applications.size())
                .attendedCount(attended)
                .createdAt(user.getCreatedAt())
                .applicationHistory(history)
                .build();
    }
}
