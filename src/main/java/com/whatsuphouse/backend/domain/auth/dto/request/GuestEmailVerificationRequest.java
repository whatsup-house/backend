package com.whatsuphouse.backend.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class GuestEmailVerificationRequest {
    @NotBlank @Email
    private String email;
}
