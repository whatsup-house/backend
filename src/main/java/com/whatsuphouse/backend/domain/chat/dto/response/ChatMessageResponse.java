package com.whatsuphouse.backend.domain.chat.dto.response;

import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSystemKind;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class ChatMessageResponse {

    @Schema(description = "메시지 ID")
    private UUID id;

    @Schema(description = "방 ID")
    private UUID roomId;

    @Schema(description = "TEXT | IMAGE | SYSTEM", example = "TEXT")
    private ChatMessageType type;

    @Schema(description = "보낸 사람. SYSTEM이면 null")
    private Sender sender;

    @Schema(description = "TEXT 본문(복호화). 삭제·IMAGE·SYSTEM이면 null", example = "안녕하세요!")
    private String content;

    @Schema(description = "IMAGE 서명 URL(1시간 유효). 그 외 null")
    private String imageUrl;

    @Schema(description = "SYSTEM 종류", example = "JOINED")
    private ChatSystemKind systemKind;

    @Schema(description = "SYSTEM 표시용 파라미터", example = "{\"nicknames\":[\"홍길동\"]}")
    private Map<String, Object> systemParams;

    @Schema(description = "링크 미리보기 {url,title,description,image}. 없으면 null")
    private Map<String, Object> linkPreview;

    @Schema(description = "리액션 집계")
    private List<Reaction> reactions;

    @Schema(description = "카톡식 안 읽은 사람 수(문의방은 관리자 전원을 1명으로 셈). SYSTEM은 0", example = "2")
    private int unreadCount;

    @Schema(description = "수정됨 여부")
    private boolean isEdited;

    @Schema(description = "삭제됨 여부(삭제된 메시지입니다)")
    private boolean isDeleted;

    private LocalDateTime createdAt;

    @Getter
    @AllArgsConstructor
    public static class Sender {

        private UUID id;

        @Schema(description = "표시명. 문의방의 관리자는 사용자에게 와썹하우스, 탈퇴 회원은 null((탈퇴한 회원) 표시)",
                example = "홍길동", nullable = true)
        private String nickname;

        private boolean isAdmin;
    }

    @Getter
    @AllArgsConstructor
    public static class Reaction {

        @Schema(example = "👍")
        private String emoji;

        @Schema(example = "3")
        private int count;

        @Schema(description = "내가 누른 리액션인지")
        private boolean isMine;
    }
}
