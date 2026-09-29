package com.whatsuphouse.backend.domain.chat.repository;

import com.whatsuphouse.backend.domain.chat.entity.ChatMute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatMuteRepository extends JpaRepository<ChatMute, UUID> {
}
