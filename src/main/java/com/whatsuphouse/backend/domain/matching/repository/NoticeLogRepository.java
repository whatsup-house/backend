package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.NoticeLog;
import com.whatsuphouse.backend.domain.matching.enums.NoticeChannel;
import com.whatsuphouse.backend.domain.matching.enums.NoticeKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NoticeLogRepository extends JpaRepository<NoticeLog, UUID> {

    long countByTableIdAndKindAndChannel(UUID tableId, NoticeKind kind, NoticeChannel channel);

    List<NoticeLog> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    List<NoticeLog> findByTableIdInAndKindOrderByCreatedAtDesc(Collection<UUID> tableIds, NoticeKind kind);
}
