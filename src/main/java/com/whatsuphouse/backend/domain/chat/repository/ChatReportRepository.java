package com.whatsuphouse.backend.domain.chat.repository;

import com.whatsuphouse.backend.domain.chat.entity.ChatReport;
import com.whatsuphouse.backend.domain.chat.enums.ChatReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatReportRepository extends JpaRepository<ChatReport, UUID> {

    List<ChatReport> findAllByOrderByCreatedAtDesc();

    List<ChatReport> findAllByStatusOrderByCreatedAtDesc(ChatReportStatus status);
}
