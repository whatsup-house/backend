package com.whatsuphouse.backend.domain.chat.dto.response;

import com.whatsuphouse.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ChatSourceMemberResponse {

    private UUID userId;

    @Schema(example = "홍길동")
    private String nickname;

    public static ChatSourceMemberResponse from(User user) {
        return new ChatSourceMemberResponse(user.getId(), user.getNickname());
    }
}
