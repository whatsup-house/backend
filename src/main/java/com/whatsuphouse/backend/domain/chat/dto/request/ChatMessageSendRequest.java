package com.whatsuphouse.backend.domain.chat.dto.request;

import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatMessageSendRequest {

    @NotNull
    @Schema(description = "TEXT 또는 IMAGE (SYSTEM 불가)", example = "TEXT")
    private ChatMessageType type;

    @NotBlank
    @Size(max = 2000, message = "메시지는 2000자 이하로 입력해주세요.")
    @Schema(description = "TEXT: 본문, IMAGE: 업로드 API가 돌려준 path", example = "안녕하세요!")
    private String content;
}
