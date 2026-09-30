package com.whatsuphouse.backend.domain.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ChatRoomIdResponse {

    private UUID roomId;
}
