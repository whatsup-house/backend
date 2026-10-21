package com.whatsuphouse.backend.domain.chat.repository;

import com.whatsuphouse.backend.domain.chat.entity.ChatReport;
import com.whatsuphouse.backend.domain.chat.enums.ChatReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatReportRepository extends JpaRepository<ChatReport, UUID> {

    List<ChatReport> findAllByOrderByCreatedAtDesc();

    @Modifying(flushAutomatically = true)
    @Query("delete from ChatReport r where r.messageId in (select m.id from ChatMessage m where m.roomId in :roomIds)")
    void deleteByRoomIdIn(@Param("roomIds") Collection<UUID> roomIds);

    List<ChatReport> findAllByStatusOrderByCreatedAtDesc(ChatReportStatus status);
}
