package com.whatsuphouse.backend.domain.dining.entity;

import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.enums.SafetyAction;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/** 예외함 한 건. 매칭·확정 파이프라인이 자동으로 처리하지 못한 건을 운영자가 처리한다. (KAN-348) */
@Entity
@Table(name = "exception_cases")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExceptionCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExceptionCaseType type;

    // 세 참조 모두 FK 없음. table_id는 dining_tables.id(V9 전 matching_groups.id와 같은 값).
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "table_id")
    private UUID tableId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExceptionCaseStatus status;

    // SAFETY 처리 조치 기록. 다른 유형은 항상 NULL.
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SafetyAction action;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    private ExceptionCase(ExceptionCaseType type, UUID sessionId, UUID tableId, UUID applicationId, String reason) {
        this.type = type;
        this.sessionId = sessionId;
        this.tableId = tableId;
        this.applicationId = applicationId;
        this.reason = reason;
        this.status = ExceptionCaseStatus.OPEN;
    }

    public static ExceptionCase open(ExceptionCaseType type, UUID sessionId, UUID tableId, UUID applicationId,
                                     String reason) {
        return new ExceptionCase(type, sessionId, tableId, applicationId, reason);
    }

    /** 처리 완료. 메모는 필수, 조치는 SAFETY만 지정할 수 있다. */
    public void resolve(UUID resolvedBy, String note, SafetyAction action) {
        if (note == null || note.isBlank()) {
            throw new CustomException(ErrorCode.EXCEPTION_NOTE_REQUIRED);
        }
        if (action != null && type != ExceptionCaseType.SAFETY) {
            throw new CustomException(ErrorCode.EXCEPTION_ACTION_NOT_ALLOWED);
        }
        this.status = ExceptionCaseStatus.RESOLVED;
        this.resolvedBy = resolvedBy;
        this.resolutionNote = note.trim();
        this.action = action;
        this.resolvedAt = LocalDateTime.now();
    }

    /** 다시 열기. 이전 메모·조치는 이력으로 남긴다(이미 적용된 참여 제한은 풀지 않는다). */
    public void reopen() {
        this.status = ExceptionCaseStatus.OPEN;
        this.resolvedBy = null;
        this.resolvedAt = null;
    }
}
