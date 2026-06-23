package com.whatsuphouse.backend.domain.auth.dto.request;

import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @Schema(example = "hong_gildong", description = "인스타그램 아이디 (선택)")
    @Size(max = 100, message = "인스타그램 아이디는 100자 이하여야 합니다.")
    private String instagramId;

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

    // 생년월일. 저장 후 나이가 필요한 응답에서 만 나이 계산 기준으로 사용한다. (KAN-257)
    // 기존 클라이언트 호환을 위해 선택값으로 두며, 미전송 시 age로만 처리한다.
    @Schema(example = "1999-03-15", description = "생년월일 (YYYY-MM-DD)")
    @Past(message = "생년월일은 과거 날짜여야 합니다.")
    private LocalDate birthDate;

    @Schema(example = "SOFTWARE_DEVELOPER", description = "직업 코드 (GET /api/jobs 목록 중 하나)")
    @Size(max = 30, message = "직업 코드는 30자 이하여야 합니다.")
    private String job;

    @Schema(example = "ENFP", description = "MBTI (선택)")
    private Mbti mbti;

    @Schema(example = "안녕하세요, 잘 부탁드려요!", description = "한줄소개 (선택)")
    @Size(max = 500, message = "한줄소개는 500자 이하여야 합니다.")
    private String intro;
}
