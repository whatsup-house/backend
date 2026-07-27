package com.whatsuphouse.backend.domain.application.client.dto.request;

import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApplicationRequest {

    @Schema(example = "김철수", description = "비회원 필수")
    private String name;

    @Pattern(regexp = "^01[0-9]{8,9}$", message = "전화번호 형식이 올바르지 않습니다.")
    @Schema(example = "01012345678", description = "비회원 필수")
    private String phone;

    @Schema(example = "MALE")
    private Gender gender;

    @Schema(example = "28")
    private Integer age;

    @Schema(example = "chulsoo_kim")
    private String instagramId;

    @Schema(example = "디자이너")
    private String job;

    @Schema(example = "ENFP")
    private Mbti mbti;

    @Schema(example = "재즈를 좋아해서 신청합니다")
    private String intro;

    @Schema(example = "이영희")
    private String referrerName;
}
