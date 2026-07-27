package com.whatsuphouse.backend.domain.user.dto.request;

import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ProfileUpdateRequest {

    @Schema(example = "새닉네임")
    @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다.")
    private String nickname;

    @Schema(example = "01087654321")
    @Pattern(regexp = "^\\d{11}$", message = "전화번호는 11자리 숫자여야 합니다.")
    private String phone;

    @Schema(example = "홍길동")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @Schema(example = "FEMALE")
    private Gender gender;

    @Schema(example = "26")
    @Min(value = 1, message = "나이는 1 이상이어야 합니다.")
    private Integer age;

    @Schema(example = "hong_gildong")
    private String instagramId;

    @Schema(example = "ENFP")
    private Mbti mbti;

    @Schema(example = "개발자")
    private String job;

    @Schema(example = "재즈와 커피를 좋아합니다")
    private String intro;
}
