package com.whatsuphouse.backend.domain.matching.entity;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.matching.enums.ResolutionChoice;
import com.whatsuphouse.backend.domain.matching.enums.ResolutionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 마지막 희망 회차에서도 못 앉은 신청에 대한 해결 제안과 참가자 선택. (설계 2.4, 4.8) */
@Entity
@Table(name = "match_resolutions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    // 제안 회차. 비어 있으면 KEEP_TICKET/REFUND만 고를 수 있다(match_status=NO_MATCH).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "offered_session_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> offeredSessionIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ResolutionChoice choice;

    @Column(name = "chosen_session_id")
    private UUID chosenSessionId;

    @Column(name = "respond_by", nullable = false)
    private LocalDateTime respondBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResolutionStatus status;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private MatchResolution(Application application, List<UUID> offeredSessionIds, LocalDateTime respondBy) {
        this.application = application;
        this.offeredSessionIds = new ArrayList<>(offeredSessionIds);
        this.respondBy = respondBy;
        this.status = ResolutionStatus.OFFERED;
    }

    public static MatchResolution offer(Application application, List<UUID> offeredSessionIds, LocalDateTime respondBy) {
        return new MatchResolution(application, offeredSessionIds, respondBy);
    }

    public boolean isOffered() {
        return status == ResolutionStatus.OFFERED;
    }

    public void resolve(ResolutionChoice choice, UUID chosenSessionId) {
        this.choice = choice;
        this.chosenSessionId = chosenSessionId;
        this.status = ResolutionStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    // 기한 경과: 이용권 보관(KEEP_TICKET)으로 자동 처리했음을 choice에 남긴다.
    public void expire() {
        this.choice = ResolutionChoice.KEEP_TICKET;
        this.status = ResolutionStatus.EXPIRED;
        this.resolvedAt = LocalDateTime.now();
    }
}
