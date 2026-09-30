package com.whatsuphouse.backend.domain.matching.entity;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 테이블 멤버(옛 matching_members, V9에서 개명). 해체된 테이블의 행은 이력으로 남으므로 한 신청이 여러 행을 가질 수 있다.
 * "활성(PROPOSED|CONFIRMED) 테이블에는 한 신청이 1개만"은 서비스가 검사한다.
 */
@Entity
@Table(name = "dining_table_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_dining_table_members_table_application",
                columnNames = {"table_id", "application_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiningTableMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private DiningTable table;

    @Column(name = "seat_order")
    private Integer seatOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "assign_reason", nullable = false, length = 20)
    private AssignReason assignReason;

    @Column(name = "is_manual", nullable = false)
    private boolean isManual = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public DiningTableMember(Application application, DiningTable table, Integer seatOrder,
                             AssignReason assignReason, boolean isManual) {
        this.application = application;
        this.table = table;
        this.seatOrder = seatOrder;
        this.assignReason = assignReason;
        this.isManual = isManual;
    }

    // 관리자가 다른 테이블로 이동시킬 때 (수동 조정으로 표시)
    public void moveTo(DiningTable table, Integer seatOrder) {
        this.table = table;
        this.seatOrder = seatOrder;
        this.isManual = true;
        this.assignReason = AssignReason.MANUAL;
    }
}
