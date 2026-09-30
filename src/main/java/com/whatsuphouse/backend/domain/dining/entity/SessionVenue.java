package com.whatsuphouse.backend.domain.dining.entity;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** 회차에서 쓸 식당과 수용 테이블 수. used_tables는 테이블에 배정된 수. PK(session_id, venue_id). (설계 2.5) */
@Entity
@Table(name = "session_venues")
@IdClass(SessionVenue.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionVenue {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Id
    @Column(name = "venue_id")
    private UUID venueId;

    @Column(name = "capacity_tables", nullable = false)
    private int capacityTables;

    @Column(name = "used_tables", nullable = false)
    private int usedTables;

    public SessionVenue(UUID sessionId, UUID venueId, int capacityTables) {
        this.sessionId = sessionId;
        this.venueId = venueId;
        this.capacityTables = capacityTables;
        this.usedTables = 0;
    }

    /** 이미 배정된 테이블 수보다 작게 줄일 수 없다. */
    public void changeCapacity(int capacityTables) {
        if (capacityTables < usedTables) {
            throw new CustomException(ErrorCode.VENUE_CAPACITY_EXCEEDED);
        }
        this.capacityTables = capacityTables;
    }

    public boolean isInUse() {
        return usedTables > 0;
    }

    public void useTable() {
        if (usedTables >= capacityTables) {
            throw new CustomException(ErrorCode.VENUE_CAPACITY_EXCEEDED);
        }
        this.usedTables++;
    }

    public void releaseTable() {
        if (usedTables > 0) {
            this.usedTables--;
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID sessionId;
        private UUID venueId;
    }
}
