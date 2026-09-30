package com.whatsuphouse.backend.domain.chat.repository;

import com.whatsuphouse.backend.domain.chat.entity.ChatReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatReactionRepository extends JpaRepository<ChatReaction, ChatReaction.Key> {

    List<ChatReaction> findAllByMessageIdIn(Collection<UUID> messageIds);
}
