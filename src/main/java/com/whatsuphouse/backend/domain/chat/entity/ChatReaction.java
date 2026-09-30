package com.whatsuphouse.backend.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_reactions")
@IdClass(ChatReaction.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "of")
public class ChatReaction {

    // 허용 이모지(표시 순서 겸용). 이 외에는 400.
    public static final List<String> ALLOWED_EMOJIS = List.of("👍", "❤️", "😂", "😮", "😢", "🙏");

    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(length = 8)
    private String emoji;

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID messageId;
        private UUID userId;
        private String emoji;
    }
}
