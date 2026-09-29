package com.whatsuphouse.backend.domain.chat.entity;

import com.whatsuphouse.backend.domain.chat.enums.ChatRoomType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSourceType;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatRoomType type;

    // GROUP만. INQUIRY 표시명은 응답 조립 시 결정한다.
    @Column(length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20)
    private ChatSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    // INQUIRY: 문의한 사용자. 사용자당 1개(UNIQUE).
    @Column(name = "inquiry_user_id", unique = true)
    private UUID inquiryUserId;

    @Column(name = "notice_message_id")
    private UUID noticeMessageId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    private ChatRoom(ChatRoomType type, String name, ChatSourceType sourceType, UUID sourceId,
                     UUID inquiryUserId, UUID createdBy) {
        this.type = type;
        this.name = name;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.inquiryUserId = inquiryUserId;
        this.createdBy = createdBy;
    }

    public static ChatRoom inquiry(UUID userId) {
        return new ChatRoom(ChatRoomType.INQUIRY, null, null, null, userId, userId);
    }

    public static ChatRoom group(String name, ChatSourceType sourceType, UUID sourceId, UUID createdBy) {
        return new ChatRoom(ChatRoomType.GROUP, name, sourceType, sourceId, null, createdBy);
    }

    public boolean isInquiry() {
        return type == ChatRoomType.INQUIRY;
    }

    public void changeNotice(UUID messageId) {
        this.noticeMessageId = messageId;
    }
}
