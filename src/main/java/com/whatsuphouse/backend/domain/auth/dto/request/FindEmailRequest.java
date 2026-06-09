package com.whatsuphouse.backend.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
public class FindEmailRequest {

    @Schema(example = "홍길동")
    @NotBlank
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @Schema(example = "01012345678")
    @NotBlank
    @Pattern(regexp = "^\\d{11}$", message = "전화번호는 11자리 숫자여야 합니다.")
    private String phone;
}
