package com.whatsuphouse.backend.domain.matching.entity;

import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/** 확정 테이블 멤버(dining_table_members) 1명의 참석. 테이블 확정 때 SCHEDULED로 만든다. 체크인·노쇼는 KAN-349. (설계 2.6) */
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
}
