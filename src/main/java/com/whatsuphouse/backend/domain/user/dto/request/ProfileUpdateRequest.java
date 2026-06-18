package com.whatsuphouse.backend.domain.user.dto.request;

import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequest {

    @Schema(example = "새닉네임")
    @NotNull(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다.")
    private String nickname;

    @Schema(example = "01087654321")
    @Pattern(regexp = "^\\d{11}$", message = "전화번호는 11자리 숫자여야 합니다.")
    private String phone;

    @Schema(example = "홍길동")
    @NotNull(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @Schema(example = "FEMALE")
    @NotNull(message = "성별은 필수입니다.")
    private Gender gender;

    @Schema(example = "26")
    @NotNull(message = "나이는 필수입니다.")
    @Min(value = 1, message = "나이는 1 이상이어야 합니다.")
    private Integer age;

    @Schema(example = "hong_gildong")
    private String instagramId;

    @Schema(example = "ENFP")
    private Mbti mbti;

    @Schema(example = "SOFTWARE_DEVELOPER", description = "직업 코드 (GET /api/jobs 목록 중 하나)")
    @Size(max = 30, message = "직업 코드는 30자 이하여야 합니다.")
    private String job;

    @Schema(example = "재즈와 커피를 좋아합니다")
    private String intro;
}
