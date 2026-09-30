package com.whatsuphouse.backend.domain.dining.entity;

import com.whatsuphouse.backend.domain.dining.enums.PeerPreferenceKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/** 같은 테이블 멤버에 대한 사람별 선호. 운영자·매칭 엔진 전용, 참가자에게 노출하지 않는다. (설계 2.6, KAN-350) */
@Entity
@Table(name = "peer_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uk_peer_preferences_from_to_table",
                columnNames = {"from_user_id", "to_user_id", "table_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PeerPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "from_user_id", nullable = false)
    private UUID fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private UUID toUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PeerPreferenceKind kind;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PeerPreference(UUID fromUserId, UUID toUserId, PeerPreferenceKind kind, UUID tableId) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.kind = kind;
        this.tableId = tableId;
    }
}
