package com.whatsuphouse.backend.domain.chat.repository;

import com.whatsuphouse.backend.domain.chat.entity.ChatReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatReactionRepository extends JpaRepository<ChatReaction, ChatReaction.Key> {

    List<ChatReaction> findAllByMessageIdIn(Collection<UUID> messageIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from ChatReaction r where r.messageId in (select m.id from ChatMessage m where m.roomId in :roomIds)")
    void deleteByRoomIdIn(@Param("roomIds") Collection<UUID> roomIds);
}
