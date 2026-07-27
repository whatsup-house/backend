package com.whatsuphouse.backend.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {

    @Schema(example = "user@example.com")
    @NotBlank
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;
}
