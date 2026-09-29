package com.whatsuphouse.backend.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatMessageUpdateRequest {

    @NotBlank
    @Size(max = 2000, message = "메시지는 2000자 이하로 입력해주세요.")
    @Schema(example = "수정한 메시지입니다.")
    private String content;
}
