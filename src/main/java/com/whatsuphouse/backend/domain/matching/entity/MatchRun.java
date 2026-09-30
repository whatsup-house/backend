package com.whatsuphouse.backend.domain.matching.entity;

import com.whatsuphouse.backend.domain.matching.enums.MatchRunTrigger;
import com.whatsuphouse.backend.domain.matching.enums.UnassignedReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 매칭 실행 1회의 집계. (설계 2.4, 4.5-8) */
@Entity
@Table(name = "match_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 20)
    private MatchRunTrigger triggeredBy;

    @Column(name = "triggered_user_id")
    private UUID triggeredUserId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "table_count", nullable = false)
    private int tableCount;

    @Column(name = "split_count", nullable = false)
    private int splitCount;

    @Column(name = "merge_count", nullable = false)
    private int mergeCount;

    @Column(name = "reallocated_count", nullable = false)
    private int reallocatedCount;

    @Column(name = "unassigned_count", nullable = false)
    private int unassignedCount;

    @Column(name = "algorithm_version", nullable = false, length = 20)
    private String algorithmVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unassigned_reasons", nullable = false, columnDefinition = "jsonb")
    private List<Unassigned> unassignedReasons = new ArrayList<>();

    public record Unassigned(UUID applicationId, UnassignedReason reason) {
    }

    private MatchRun(UUID sessionId, MatchRunTrigger triggeredBy, UUID triggeredUserId, String algorithmVersion) {
        this.sessionId = sessionId;
        this.triggeredBy = triggeredBy;
        this.triggeredUserId = triggeredUserId;
        this.algorithmVersion = algorithmVersion;
        this.startedAt = LocalDateTime.now();
    }

    public static MatchRun start(UUID sessionId, MatchRunTrigger triggeredBy, UUID triggeredUserId,
                                 String algorithmVersion) {
        return new MatchRun(sessionId, triggeredBy, triggeredUserId, algorithmVersion);
    }

    public void finish(int candidateCount, int tableCount, int splitCount, int mergeCount, int reallocatedCount,
                       List<Unassigned> unassignedReasons) {
        this.candidateCount = candidateCount;
        this.tableCount = tableCount;
        this.splitCount = splitCount;
        this.mergeCount = mergeCount;
        this.reallocatedCount = reallocatedCount;
        this.unassignedCount = unassignedReasons.size();
        this.unassignedReasons = new ArrayList<>(unassignedReasons);
        this.finishedAt = LocalDateTime.now();
    }
}
