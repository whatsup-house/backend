package com.whatsuphouse.backend.domain.matching.entity;

import com.whatsuphouse.backend.domain.application.entity.Application;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "matching_members")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private MatchingGroup group;

    @Column(name = "seat_order")
    private Integer seatOrder;

    @Column(name = "is_manual_assign", nullable = false)
    private boolean isManualAssign = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MatchingMember(Application application, MatchingGroup group,
                          Integer seatOrder, boolean isManualAssign) {
        this.application = application;
        this.group = group;
        this.seatOrder = seatOrder;
        this.isManualAssign = isManualAssign;
    }

    // 관리자가 다른 그룹으로 이동시킬 때 (수동 조정으로 표시)
    public void moveTo(MatchingGroup group, Integer seatOrder) {
        this.group = group;
        this.seatOrder = seatOrder;
        this.isManualAssign = true;
    }
}
