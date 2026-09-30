package com.whatsuphouse.backend.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PushSubscriptionDeleteRequest {

    @NotBlank
    @Size(max = 2048)
    @Schema(example = "https://fcm.googleapis.com/fcm/send/dXNlcl9lbmRwb2ludA")
    private String endpoint;
}
