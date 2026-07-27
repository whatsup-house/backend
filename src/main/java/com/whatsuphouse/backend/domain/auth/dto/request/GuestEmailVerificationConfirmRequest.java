package com.whatsuphouse.backend.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class GuestEmailVerificationConfirmRequest {
    @NotBlank @Email
    private String email;
    @NotBlank @Pattern(regexp = "\\d{6}")
    private String code;
}
