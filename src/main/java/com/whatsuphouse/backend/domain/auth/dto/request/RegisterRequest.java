package com.whatsuphouse.backend.domain.auth.dto.request;

import com.whatsuphouse.backend.global.common.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class RegisterRequest {

    @Schema(example = "user@example.com")
    @NotBlank
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @Schema(example = "password123!")
    @NotBlank
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "비밀번호는 영문과 숫자를 포함해야 합니다.")
    private String password;

    @Schema(example = "홍길동닉네임")
    @NotBlank
    @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다.")
    private String nickname;

    @Schema(example = "01012345678")
    @Pattern(regexp = "^\\d{11}$", message = "전화번호는 11자리 숫자여야 합니다.")
    private String phone;

    @Schema(example = "홍길동")
    @NotBlank
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @Schema(example = "MALE")
    @NotNull(message = "성별을 입력해주세요.")
    private Gender gender;

    @Schema(example = "25")
    @NotNull(message = "나이를 입력해주세요.")
    @Min(value = 1, message = "나이는 1 이상이어야 합니다.")
    private Integer age;
}
