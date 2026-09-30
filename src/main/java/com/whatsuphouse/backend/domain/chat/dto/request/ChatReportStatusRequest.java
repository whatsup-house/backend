package com.whatsuphouse.backend.domain.chat.dto.request;

import com.whatsuphouse.backend.domain.chat.enums.ChatReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatReportStatusRequest {

    @NotNull
    @Schema(example = "RESOLVED")
    private ChatReportStatus status;
}
