package com.whatsuphouse.backend.domain.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class GuestEmailVerificationResponse {
    private boolean accepted;
    private boolean verified;
}
