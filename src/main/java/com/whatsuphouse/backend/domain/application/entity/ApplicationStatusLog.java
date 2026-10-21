package com.whatsuphouse.backend.domain.application.entity;

import com.whatsuphouse.backend.domain.application.enums.StatusLogField;
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

/** 신청 상태 변경 이력 한 줄. 항목마다 직전 기록 값(from)과 바뀐 값(to). 첫 기록은 from = null. (KAN-437) */
@Entity
@Table(name = "application_status_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // FK 없음(취소·삭제된 신청의 이력도 남긴다).
    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusLogField field;

    @Column(name = "from_value", length = 30)
    private String fromValue;

    @Column(name = "to_value", length = 30)
    private String toValue;

    // 바꾼 회원(관리자·참가자). NULL = 시스템(스케줄러·웹훅).
    @Column(name = "actor_id")
    private UUID actorId;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ApplicationStatusLog of(UUID applicationId, StatusLogField field, String fromValue, String toValue,
                                          UUID actorId, String reason) {
        ApplicationStatusLog log = new ApplicationStatusLog();
        log.applicationId = applicationId;
        log.field = field;
        log.fromValue = fromValue;
        log.toValue = toValue;
        log.actorId = actorId;
        log.reason = reason;
        log.createdAt = LocalDateTime.now();
        return log;
    }
}
