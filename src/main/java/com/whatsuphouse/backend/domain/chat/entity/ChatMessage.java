package com.whatsuphouse.backend.domain.chat.entity;

import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSystemKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_room_created", columnList = "room_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    // SYSTEM은 NULL
    @Column(name = "sender_id")
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatMessageType type;

    // AES-256-GCM 암호문(태그 포함). TEXT=본문, IMAGE=스토리지 경로, SYSTEM=NULL
    @Column(name = "content_enc", columnDefinition = "bytea")
    private byte[] contentEnc;

    @Column(columnDefinition = "bytea")
    private byte[] nonce;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_kind", length = 30)
    private ChatSystemKind systemKind;

    // 표시용 평문(닉네임 등). 암호화하지 않는다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "system_params", columnDefinition = "jsonb")
    private Map<String, Object> systemParams;

    // 링크 미리보기 일감이 채운다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "link_preview", columnDefinition = "jsonb")
    private Map<String, Object> linkPreview;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ChatMessage(UUID roomId, UUID senderId, ChatMessageType type, byte[] contentEnc, byte[] nonce,
                        ChatSystemKind systemKind, Map<String, Object> systemParams) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.type = type;
        this.contentEnc = contentEnc;
        this.nonce = nonce;
        this.systemKind = systemKind;
        this.systemParams = systemParams;
        // DB(TIMESTAMP) 정밀도에 맞춰 커서·읽음 비교가 메모리/DB 간에 어긋나지 않게 한다.
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    public static ChatMessage user(UUID roomId, UUID senderId, ChatMessageType type, byte[] contentEnc, byte[] nonce) {
        return new ChatMessage(roomId, senderId, type, contentEnc, nonce, null, null);
    }

    public static ChatMessage system(UUID roomId, ChatSystemKind kind, Map<String, Object> params) {
        return new ChatMessage(roomId, null, ChatMessageType.SYSTEM, null, null, kind, params);
    }

    public void edit(byte[] contentEnc, byte[] nonce) {
        this.contentEnc = contentEnc;
        this.nonce = nonce;
        this.editedAt = LocalDateTime.now();
    }

    /** {url,title,description,image}. 링크 미리보기 비동기 처리가 채운다. */
    public void attachLinkPreview(Map<String, Object> linkPreview) {
        this.linkPreview = linkPreview;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
