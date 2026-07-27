package com.whatsuphouse.backend.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserWithdrawResponse {

    private boolean withdrawn;
}
