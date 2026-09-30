package com.whatsuphouse.backend.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** /user/queue/rooms 방 목록 갱신(메시지 생성 시 멤버별). 목록에 없는 roomId면 FE가 방 목록을 다시 조회한다. */
@Getter
@AllArgsConstructor
public class ChatRoomPreviewResponse {

    private UUID roomId;

    @Schema(description = "방금 생성된 마지막 메시지")
    private ChatLastMessageResponse lastMessage;

    @Schema(description = "받는 멤버의 안읽은 수(SYSTEM·내 메시지·삭제 제외)", example = "3")
    private int unreadCount;
}
