package com.whatsuphouse.backend.domain.user.admin.dto.response;

import com.whatsuphouse.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class UserListResponse {

    @Schema(description = "회원 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "이메일", example = "user@example.com")
    private String email;

    @Schema(description = "닉네임", example = "홍길동")
    private String nickname;

    @Schema(description = "전화번호", example = "01012345678")
    private String phone;

    @Schema(description = "관리자 여부", example = "false")
    private boolean isAdmin;

    @Schema(description = "마일리지 잔액", example = "1000")
    private Integer mileage;

    @Schema(description = "총 신청 횟수 (취소 제외)", example = "5")
    private long totalApplications;

    @Schema(description = "실제 참여 횟수", example = "3")
    private long attendedCount;

    @Schema(description = "가입일시", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    public static UserListResponse of(User user, long totalApplications, long attendedCount) {
        return UserListResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .isAdmin(user.isAdmin())
                .mileage(user.getMileageBalance())
                .totalApplications(totalApplications)
                .attendedCount(attendedCount)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
