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
 * 취소한 멤버는 행을 남기고 removed_at만 채운다(KAN-347). "활성(PROPOSED|CONFIRMED) 테이블의 제거되지 않은 멤버는
 * 한 신청당 1개만"은 서비스가 검사한다.
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

    // 취소로 테이블에서 빠진 시각. 행은 참석 기록(attendances)이 참조하므로 지우지 않는다. NULL이면 활성 멤버. (KAN-347)
    @Column(name = "removed_at")
    private LocalDateTime removedAt;

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
        moveTo(table, seatOrder, AssignReason.MANUAL, true);
    }

    // 다른 테이블로 옮긴다. 확정 후 취소 재조정(KAN-347)은 REALLOCATED·자동으로 옮긴다.
    public void moveTo(DiningTable table, Integer seatOrder, AssignReason assignReason, boolean isManual) {
        this.table = table;
        this.seatOrder = seatOrder;
        this.assignReason = assignReason;
        this.isManual = isManual;
    }

    // 취소한 멤버를 테이블에서 뺀다(행은 이력·참석 기록용으로 남긴다). 이후 인원·검증·조회에서 제외된다.
    public void remove() {
        this.removedAt = LocalDateTime.now();
    }
}
