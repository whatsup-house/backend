package com.whatsuphouse.backend.domain.dining.entity;

import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.SafetyAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 같은 테이블 멤버 신고. 접수 시 예외함(SAFETY) 건을 함께 만들고, 운영자가 그 건을 처리하면 상태·조치·메모를 따라간다.
 * 운영자·매칭 엔진 전용, 참가자에게 노출하지 않는다. (설계 2.6, KAN-350)
 */
@Entity
@Table(name = "safety_reports")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "reported_user_id", nullable = false)
    private UUID reportedUserId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    // 연결된 예외 건의 상태를 따른다(OPEN|RESOLVED).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExceptionCaseStatus status = ExceptionCaseStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SafetyAction action;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "exception_case_id")
    private UUID exceptionCaseId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public SafetyReport(UUID reporterId, UUID reportedUserId, UUID tableId, String reason, UUID exceptionCaseId) {
        this.reporterId = reporterId;
        this.reportedUserId = reportedUserId;
        this.tableId = tableId;
        this.reason = reason;
        this.exceptionCaseId = exceptionCaseId;
    }

    /** 연결된 예외 건의 처리 결과(처리·다시 열기)를 그대로 옮긴다. */
    public void syncWith(ExceptionCase exceptionCase) {
        this.status = exceptionCase.getStatus();
        this.action = exceptionCase.getAction();
        this.adminNote = exceptionCase.getResolutionNote();
        this.resolvedAt = exceptionCase.getResolvedAt();
    }
}
