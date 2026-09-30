package com.whatsuphouse.backend.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 브라우저 PushSubscription.toJSON() 형태 그대로 받는다. */
@Getter
@NoArgsConstructor
public class PushSubscriptionCreateRequest {

    @NotBlank
    @Size(max = 2048)
    @Pattern(regexp = "https://\\S+")
    @Schema(example = "https://fcm.googleapis.com/fcm/send/dXNlcl9lbmRwb2ludA")
    private String endpoint;

    @Valid
    @NotNull
    private Keys keys;

    @Getter
    @NoArgsConstructor
    public static class Keys {

        @NotBlank
        @Size(max = 256)
        @Schema(example = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM")
        private String p256dh;

        @NotBlank
        @Size(max = 256)
        @Schema(example = "tBHItJI5svbpez7KI4CCXg")
        private String auth;
    }
}
