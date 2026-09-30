package com.whatsuphouse.backend.domain.matching.entity;

import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 확정 테이블 멤버(dining_table_members) 1명의 참석. 확정 테이블에 앉을 때 SCHEDULED로 만든다. (설계 2.6)
 * 본인 체크인 → ATTENDED, 사전 취소 → CANCELED_EARLY, 종료 후 미체크인 → 노쇼 후보 표시, 운영자가 최종 상태를 정한다. (KAN-349)
 */
@Entity
@Table(name = "attendances",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendances_table_member", columnNames = "table_member_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "table_member_id", nullable = false)
    private UUID tableMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    // 종료 후 체크인이 없는 SCHEDULED에 표시(상태는 그대로). 운영자가 노쇼를 확정한다.
    @Column(name = "no_show_candidate", nullable = false)
    private boolean noShowCandidate = false;

    // 마지막으로 상태를 바꾼 관리자. 시스템 처리는 NULL.
    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Attendance(UUID tableMemberId) {
        this.tableMemberId = tableMemberId;
        this.status = AttendanceStatus.SCHEDULED;
    }

    public static Attendance schedule(UUID tableMemberId) {
        return new Attendance(tableMemberId);
    }

    /** 본인 체크인. 이미 ATTENDED면 그대로(멱등), SCHEDULED가 아니면 400. */
    public void checkIn(LocalDateTime now) {
        if (status == AttendanceStatus.ATTENDED) {
            return;
        }
        if (status != AttendanceStatus.SCHEDULED) {
            throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = AttendanceStatus.ATTENDED;
        this.checkedInAt = now;
        this.noShowCandidate = false;
    }

    /** 사전 취소(신청 취소) 시스템 처리. 예정 상태일 때만 바꾼다. */
    public void cancelEarly() {
        if (status == AttendanceStatus.SCHEDULED) {
            this.status = AttendanceStatus.CANCELED_EARLY;
        }
    }

    /** 회차 종료 후 체크인이 없는 예정 참석을 노쇼 후보로 표시한다(상태는 그대로). */
    public void markNoShowCandidate() {
        if (status == AttendanceStatus.SCHEDULED) {
            this.noShowCandidate = true;
        }
    }

    /**
     * 운영자 상태 확정(ATTENDED·NO_SHOW·CANCELED_LATE). SCHEDULED(초기 상태)와 CANCELED_EARLY(신청 취소 흐름이 이용권 복원과 함께 남김)는
     * 시스템만 만들고, 사전 취소된 참석은 바꿀 수 없다. 확정하면 노쇼 후보 표시는 내린다.
     */
    public void changeStatus(AttendanceStatus status, UUID adminId) {
        if (status == AttendanceStatus.SCHEDULED || status == AttendanceStatus.CANCELED_EARLY
                || this.status == AttendanceStatus.CANCELED_EARLY) {
            throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = status;
        this.updatedBy = adminId;
        this.noShowCandidate = false;
    }
}
