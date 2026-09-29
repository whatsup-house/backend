package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.domain.chat.entity.ChatMember;
import com.whatsuphouse.backend.domain.chat.entity.ChatMessage;
import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSystemKind;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** docs/chat-design.md 2절 권한표 전수: 행위 × (일반 사용자, 관리자) */
class ChatAccessPolicyTest {

    private static final boolean USER = false;
    private static final boolean ADMIN = true;

    private final ChatAccessPolicy policy = new ChatAccessPolicy();

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final ChatRoom inquiry = ChatRoom.inquiry(me);
    private final ChatRoom group = ChatRoom.group("단체방", null, null, other);

    private ChatMember activeMember() {
        return ChatMember.join(UUID.randomUUID(), me, null);
    }

    private ChatMember leftMember() {
        ChatMember member = activeMember();
        member.leave();
        return member;
    }

    private ChatMessage message(UUID senderId, ChatMessageType type) {
        return ChatMessage.user(UUID.randomUUID(), senderId, type, new byte[0], new byte[0]);
    }

    private static void assertDenied(Executable action, ErrorCode expected) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    @Test
    @DisplayName("문의방 시작: 사용자 ✓ / 관리자 ✗")
    void openInquiry() {
        assertDoesNotThrow(() -> policy.checkOpenInquiry(USER));
        assertDenied(() -> policy.checkOpenInquiry(ADMIN), ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("단체방 생성·공지·뮤트·신고 처리: 사용자 ✗ / 관리자 ✓")
    void adminOnlyActions() {
        assertDenied(() -> policy.checkAdmin(USER), ErrorCode.FORBIDDEN);
        assertDoesNotThrow(() -> policy.checkAdmin(ADMIN));
    }

    @Test
    @DisplayName("멤버 추가·내보내기·방 삭제: 관리자 & 단체방만")
    void manageGroup() {
        assertDenied(() -> policy.checkManageGroup(group, USER), ErrorCode.FORBIDDEN);
        assertDoesNotThrow(() -> policy.checkManageGroup(group, ADMIN));
        assertDenied(() -> policy.checkManageGroup(inquiry, ADMIN), ErrorCode.CHAT_ROOM_TYPE_MISMATCH);
    }

    @Test
    @DisplayName("메시지 전송: 멤버 & left_at NULL & 미뮤트 & 계정 정상 (사용자·관리자 동일)")
    void send() {
        assertDoesNotThrow(() -> policy.checkSend(activeMember(), false, true));
        assertThat(policy.canSend(activeMember(), false, true)).isTrue();

        assertDenied(() -> policy.checkSend(null, false, true), ErrorCode.CHAT_NOT_MEMBER);
        assertDenied(() -> policy.checkSend(leftMember(), false, true), ErrorCode.CHAT_NOT_MEMBER);
        assertDenied(() -> policy.checkSend(activeMember(), true, true), ErrorCode.CHAT_MUTED);
        assertDenied(() -> policy.checkSend(activeMember(), false, false), ErrorCode.FORBIDDEN);

        assertThat(policy.canSend(null, false, true)).isFalse();
        assertThat(policy.canSend(leftMember(), false, true)).isFalse();
        assertThat(policy.canSend(activeMember(), true, true)).isFalse();
        assertThat(policy.canSend(activeMember(), false, false)).isFalse();
    }

    @Test
    @DisplayName("방 조회: 참여 중인 멤버만(나간 멤버·비멤버는 CHAT_NOT_MEMBER)")
    void readRoom() {
        assertDoesNotThrow(() -> policy.checkMember(activeMember()));
        assertDenied(() -> policy.checkMember(leftMember()), ErrorCode.CHAT_NOT_MEMBER);
        assertDenied(() -> policy.checkMember(null), ErrorCode.CHAT_NOT_MEMBER);
    }

    @Test
    @DisplayName("수정: 본인 TEXT만 ✓, 타인 메시지·본인 IMAGE ✗(403), 뮤트 중 ✗")
    void edit() {
        assertDoesNotThrow(() -> policy.checkEdit(activeMember(), message(me, ChatMessageType.TEXT), me, false));

        assertDenied(() -> policy.checkEdit(activeMember(), message(other, ChatMessageType.TEXT), me, false),
                ErrorCode.CHAT_MESSAGE_FORBIDDEN);
        assertDenied(() -> policy.checkEdit(activeMember(), message(me, ChatMessageType.IMAGE), me, false),
                ErrorCode.CHAT_MESSAGE_FORBIDDEN);
        assertDenied(() -> policy.checkEdit(activeMember(), message(me, ChatMessageType.TEXT), me, true),
                ErrorCode.CHAT_MUTED);
        assertDenied(() -> policy.checkEdit(leftMember(), message(me, ChatMessageType.TEXT), me, false),
                ErrorCode.CHAT_NOT_MEMBER);
    }

    @Test
    @DisplayName("삭제: 본인 ✓ / 타인 메시지는 사용자 ✗, 관리자 ✓(비멤버여도)")
    void delete() {
        assertDoesNotThrow(() -> policy.checkDelete(activeMember(), message(me, ChatMessageType.TEXT), me, USER));
        assertDenied(() -> policy.checkDelete(activeMember(), message(other, ChatMessageType.TEXT), me, USER),
                ErrorCode.CHAT_MESSAGE_FORBIDDEN);
        assertDenied(() -> policy.checkDelete(null, message(me, ChatMessageType.TEXT), me, USER),
                ErrorCode.CHAT_NOT_MEMBER);

        assertDoesNotThrow(() -> policy.checkDelete(activeMember(), message(other, ChatMessageType.TEXT), me, ADMIN));
        assertDoesNotThrow(() -> policy.checkDelete(null, message(other, ChatMessageType.IMAGE), me, ADMIN));
        assertDoesNotThrow(() -> policy.checkDelete(null,
                ChatMessage.system(UUID.randomUUID(), ChatSystemKind.JOINED, Map.of()), me, ADMIN));
    }

    @Test
    @DisplayName("조용히 나가기: 단체방만(사용자·관리자 동일), 문의방은 CHAT_ROOM_TYPE_MISMATCH")
    void leave() {
        assertDoesNotThrow(() -> policy.checkLeave(group));
        assertThat(policy.canLeave(group)).isTrue();
        assertDenied(() -> policy.checkLeave(inquiry), ErrorCode.CHAT_ROOM_TYPE_MISMATCH);
        assertThat(policy.canLeave(inquiry)).isFalse();
    }

    @Test
    @DisplayName("문의방 숨기기: 사용자 ✓ / 관리자 ✗, 단체방은 CHAT_ROOM_TYPE_MISMATCH")
    void hide() {
        assertDoesNotThrow(() -> policy.checkHide(inquiry, USER));
        assertThat(policy.canHide(inquiry, USER)).isTrue();

        assertDenied(() -> policy.checkHide(inquiry, ADMIN), ErrorCode.FORBIDDEN);
        assertThat(policy.canHide(inquiry, ADMIN)).isFalse();

        assertDenied(() -> policy.checkHide(group, USER), ErrorCode.CHAT_ROOM_TYPE_MISMATCH);
        assertThat(policy.canHide(group, USER)).isFalse();
    }

    @Test
    @DisplayName("신고: 참여 중인 사용자 ✓ / 관리자 ✗(목록 처리) / 비멤버 ✗")
    void report() {
        assertDoesNotThrow(() -> policy.checkReport(activeMember(), USER));
        assertDenied(() -> policy.checkReport(activeMember(), ADMIN), ErrorCode.FORBIDDEN);
        assertDenied(() -> policy.checkReport(leftMember(), USER), ErrorCode.CHAT_NOT_MEMBER);
    }
}
