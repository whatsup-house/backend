package com.whatsuphouse.backend.domain.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private UserInfo user;

    @Getter
    @Builder
    public static class UserInfo {
        private UUID id;
        private String email;
        private String nickname;
        private boolean isAdmin;
    }
}
