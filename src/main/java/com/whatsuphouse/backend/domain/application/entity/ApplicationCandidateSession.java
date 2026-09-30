package com.whatsuphouse.backend.domain.application.entity;

import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/** 신청자가 고른 희망 회차. priority 1이 1순위. REGULAR 신청은 신청 회차 1행. (KAN-337) */
@Entity
@Table(name = "application_candidate_sessions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "session_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationCandidateSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GatheringSession session;

    @Column(nullable = false)
    private int priority;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ApplicationCandidateSession(Application application, GatheringSession session, int priority) {
        this.application = application;
        this.session = session;
        this.priority = priority;
    }
}
