package com.whatsuphouse.backend.domain.chat.dto.response;

import com.whatsuphouse.backend.domain.chat.enums.ChatRoomType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ChatRoomDetailResponse {

    private UUID id;

    private ChatRoomType type;

    @Schema(description = "표시명(방 목록과 동일 규칙)")
    private String name;

    private ChatSourceType sourceType;

    private UUID sourceId;

    private int memberCount;

    @Schema(description = "참여 중 멤버")
    private List<Member> members;

    @Schema(description = "공지 메시지. 없거나 삭제됐으면 null")
    private ChatMessageResponse notice;

    @Schema(description = "내 권한")
    private Permissions permissions;

    @Getter
    @AllArgsConstructor
    public static class Member {

        private UUID userId;

        @Schema(description = "표시명(메시지 sender와 동일 규칙)", nullable = true)
        private String nickname;

        private boolean isAdmin;
    }

    @Getter
    @Builder
    public static class Permissions {

        @Schema(description = "메시지 전송 가능(멤버 & 미뮤트 & 계정 정상)")
        private boolean canSend;

        private boolean isMuted;

        @Schema(description = "조용히 나가기 가능(GROUP)")
        private boolean canLeave;

        @Schema(description = "숨기기 가능(INQUIRY & 사용자)")
        private boolean canHide;

        @Schema(description = "관리자 액션(멤버 관리·공지·타인 메시지 삭제) 가능")
        private boolean isAdmin;
    }
}
