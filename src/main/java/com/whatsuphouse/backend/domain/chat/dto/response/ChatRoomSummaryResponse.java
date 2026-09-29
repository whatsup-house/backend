package com.whatsuphouse.backend.domain.chat.dto.response;

import com.whatsuphouse.backend.domain.chat.enums.ChatRoomType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ChatRoomSummaryResponse {

    private UUID id;

    @Schema(example = "GROUP")
    private ChatRoomType type;

    @Schema(description = "표시명. GROUP=방 이름, INQUIRY=사용자에겐 와썹하우스 / 관리자에겐 문의자 닉네임(탈퇴 시 null)",
            example = "10월 재즈 게더링")
    private String name;

    private ChatSourceType sourceType;

    private UUID sourceId;

    @Schema(description = "참여 중 멤버 수", example = "8")
    private int memberCount;

    @Schema(description = "마지막 메시지. 없으면 null")
    private ChatLastMessageResponse lastMessage;

    @Schema(description = "안읽은 수(SYSTEM·내 메시지·삭제 제외)", example = "3")
    private int unreadCount;

    @Schema(description = "문의방 미답변 여부(마지막 메시지를 문의자가 보냄)")
    private boolean isUnanswered;

    @Schema(description = "내가 참여 중인지(관리자 전체 목록용)")
    private boolean isMember;
}
