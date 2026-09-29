package com.whatsuphouse.backend.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * /topic/rooms/{roomId} 이벤트 봉투.
 * payload: MESSAGE_* · REACTION_CHANGED = ChatMessageResponse, NOTICE_CHANGED = 공지 ChatMessageResponse(해제면 null),
 * READ = {userId, messageId}, MEMBER_CHANGED = {userIds}(추가·재초대·내보내기·나가기 대상. 목록은 방 상세로 다시 조회).
 * 방 전체에 한 번 보내므로 뷰어 중립: sender 표시명은 비관리자 시점, reactions[].isMine은 항상 false.
 */
@Getter
@AllArgsConstructor
public class ChatSocketEventResponse {

    public enum Kind {
        MESSAGE_CREATED, MESSAGE_UPDATED, MESSAGE_DELETED, REACTION_CHANGED, READ, NOTICE_CHANGED, MEMBER_CHANGED
    }

    @Schema(example = "MESSAGE_CREATED")
    private Kind kind;

    private UUID roomId;

    private Object payload;
}
