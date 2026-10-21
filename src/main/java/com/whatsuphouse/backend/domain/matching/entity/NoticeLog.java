package com.whatsuphouse.backend.domain.matching.entity;

import com.whatsuphouse.backend.domain.matching.enums.NoticeChannel;
import com.whatsuphouse.backend.domain.matching.enums.NoticeKind;
import com.whatsuphouse.backend.domain.matching.enums.NoticeResult;
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

import java.time.LocalDateTime;
import java.util.UUID;

/** 우연한 식탁 안내 발송 시도 1건(INF-08). 같은 테이블·종류·채널의 몇 번째 시도인지 attempt에 남긴다. (KAN-437) */
@Entity
@Table(name = "notice_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeLog {

    public static final int ERROR_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 참조는 FK 없음(exception_cases와 같다).
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "table_id")
    private UUID tableId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NoticeKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeResult status;

    @Column(nullable = false)
    private int attempt;

    @Column(length = ERROR_MAX_LENGTH)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** error가 null이면 성공, 있으면 실패(300자까지). */
    public static NoticeLog of(UUID sessionId, UUID tableId, NoticeKind kind, NoticeChannel channel, int attempt, String error) {
        NoticeLog log = new NoticeLog();
        log.sessionId = sessionId;
        log.tableId = tableId;
        log.kind = kind;
        log.channel = channel;
        log.status = error == null ? NoticeResult.SUCCESS : NoticeResult.FAILED;
        log.attempt = attempt;
        log.error = error == null || error.length() <= ERROR_MAX_LENGTH ? error : error.substring(0, ERROR_MAX_LENGTH);
        log.createdAt = LocalDateTime.now();
        return log;
    }
}
