package com.whatsuphouse.backend.domain.user.admin.dto.request;

import com.whatsuphouse.backend.domain.user.enums.UserAccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserStatusRequest {

    @NotNull
    private UserAccountStatus status;
}
