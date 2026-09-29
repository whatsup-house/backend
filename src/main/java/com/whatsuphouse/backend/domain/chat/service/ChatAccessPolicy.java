package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.domain.chat.entity.ChatMember;
import com.whatsuphouse.backend.domain.chat.entity.ChatMessage;
import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.enums.ChatRoomType;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 채팅 권한표(docs/chat-design.md 2절)를 한 곳에 모은다. 판정에 필요한 상태는 호출 측이 넘긴다.
 *
 * | 행위                               | 일반 사용자 | 관리자          |
 * |------------------------------------|-------------|-----------------|
 * | 문의방 시작                        | 본인 1개    | ✗(관리자 목록)  |
 * | 단체방 생성/멤버 추가·내보내기/삭제 | ✗           | ✓ (GROUP만)     |
 * | 메시지 전송                        | 멤버 & left_at NULL & 미뮤트 & 계정 정상 (동일) |
 * | 본인 메시지 수정(TEXT)·삭제        | ✓           | ✓               |
 * | 타인 메시지 삭제                   | ✗           | ✓               |
 * | 공지 등록/해제                     | ✗           | ✓               |
 * | 조용히 나가기                      | GROUP만     | GROUP만         |
 * | 문의방 숨기기                      | ✓           | ✗               |
 * | 신고                               | ✓           | ✗(목록 처리)    |
 * | 뮤트·신고 처리                     | ✗           | ✓               |
 */
@Component
public class ChatAccessPolicy {

    public boolean isActiveMember(ChatMember member) {
        return member != null && member.isActive();
    }

    /** 방 안 행위(조회·리액션 등)의 공통 전제: 참여 중(left_at NULL)인 멤버. member는 없으면 null. */
    public void checkMember(ChatMember member) {
        if (!isActiveMember(member)) {
            throw new CustomException(ErrorCode.CHAT_NOT_MEMBER);
        }
    }

    public boolean canSend(ChatMember member, boolean muted, boolean accountActive) {
        return isActiveMember(member) && !muted && accountActive;
    }

    public void checkSend(ChatMember member, boolean muted, boolean accountActive) {
        checkMember(member);
        if (muted) {
            throw new CustomException(ErrorCode.CHAT_MUTED);
        }
        if (!accountActive) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    /** 본인 TEXT만. 뮤트 중 수정은 전송 우회라 막는다. */
    public void checkEdit(ChatMember member, ChatMessage message, UUID userId, boolean muted) {
        checkMember(member);
        if (!userId.equals(message.getSenderId()) || message.getType() != ChatMessageType.TEXT) {
            throw new CustomException(ErrorCode.CHAT_MESSAGE_FORBIDDEN);
        }
        if (muted) {
            throw new CustomException(ErrorCode.CHAT_MUTED);
        }
    }

    /** 본인(참여 중) 또는 관리자(타인 메시지 포함, 신고 처리 등으로 비멤버여도 가능). */
    public void checkDelete(ChatMember member, ChatMessage message, UUID userId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        checkMember(member);
        if (!userId.equals(message.getSenderId())) {
            throw new CustomException(ErrorCode.CHAT_MESSAGE_FORBIDDEN);
        }
    }

    /** 단체방 생성, 공지 등록/해제, 뮤트, 신고 처리 */
    public void checkAdmin(boolean isAdmin) {
        if (!isAdmin) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    /** 멤버 추가·내보내기, 방 삭제: 관리자 & GROUP만 */
    public void checkManageGroup(ChatRoom room, boolean isAdmin) {
        checkAdmin(isAdmin);
        if (room.getType() != ChatRoomType.GROUP) {
            throw new CustomException(ErrorCode.CHAT_ROOM_TYPE_MISMATCH);
        }
    }

    public boolean canLeave(ChatRoom room) {
        return room.getType() == ChatRoomType.GROUP;
    }

    /** 조용히 나가기: GROUP만(사용자·관리자 동일). 문의방은 나가기 불가, 숨기기만. */
    public void checkLeave(ChatRoom room) {
        if (!canLeave(room)) {
            throw new CustomException(ErrorCode.CHAT_ROOM_TYPE_MISMATCH);
        }
    }

    public boolean canHide(ChatRoom room, boolean isAdmin) {
        return room.getType() == ChatRoomType.INQUIRY && !isAdmin;
    }

    /** 문의방 숨기기: INQUIRY만, 사용자만 */
    public void checkHide(ChatRoom room, boolean isAdmin) {
        if (room.getType() != ChatRoomType.INQUIRY) {
            throw new CustomException(ErrorCode.CHAT_ROOM_TYPE_MISMATCH);
        }
        if (isAdmin) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    /** 문의방 시작: 사용자 본인만. 관리자는 관리자 방 목록에서 연다. */
    public void checkOpenInquiry(boolean isAdmin) {
        if (isAdmin) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    /** 신고: 참여 중인 사용자. 관리자는 신고 목록을 처리한다. */
    public void checkReport(ChatMember member, boolean isAdmin) {
        checkMember(member);
        if (isAdmin) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
