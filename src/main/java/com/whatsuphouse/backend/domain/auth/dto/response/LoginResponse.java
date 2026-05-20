package com.whatsuphouse.backend.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class LoginResponse {

    @JsonIgnore
    private String accessToken;
    @JsonIgnore
    private String refreshToken;
    private UserInfo user;

    @Getter
    @Builder
    public static class UserInfo {
        private UUID id;
        private String email;
        private String nickname;
        private boolean isAdmin;
        private Integer mileage;
    }
}
