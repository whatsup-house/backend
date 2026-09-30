package com.whatsuphouse.backend.domain.gathering.entity;

import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.global.common.BaseEntity;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 모임 회차. 모임 종류(Gathering) 하나 아래 날짜·시간·장소·정원이 다른 회차가 여러 개 붙는다. (KAN-337)
 * V5 마이그레이션으로 옮겨진 기존 회차는 옛 게더링 ID를 그대로 회차 ID로 쓴다.
 */
@Entity
@Table(name = "gathering_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatheringSession extends BaseEntity {

    private static final int DEFAULT_TABLE_SIZE_MIN = 4;
    private static final int DEFAULT_TABLE_SIZE_MAX = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "max_attendees", nullable = false)
    private int maxAttendees;

    // NULL이면 종류의 기본 가격(Gathering.basePrice)을 쓴다.
    @Column(name = "price_override")
    private Integer priceOverride;

    @Column(name = "apply_deadline_at")
    private LocalDateTime applyDeadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GatheringSessionStatus status = GatheringSessionStatus.OPEN;

    // ── RANDOM_TABLE 전용. 그 외 타입은 NULL. NULL이면 매칭 규칙 기본값을 쓴다. ──

    @Column(name = "match_run_at")
    private LocalDateTime matchRunAt;

    @Column(name = "auto_confirm_grace_minutes")
    private Integer autoConfirmGraceMinutes;

    @Column(name = "table_size_min")
    private Integer tableSizeMin;

    @Column(name = "table_size_max")
    private Integer tableSizeMax;

    @Column(name = "min_group_score", precision = 5, scale = 4)
    private BigDecimal minGroupScore;

    @Column(name = "max_age_gap")
    private Integer maxAgeGap;

    @Builder
    public GatheringSession(Gathering gathering, LocalDate eventDate, LocalTime startTime, LocalTime endTime,
                            Location location, int maxAttendees, Integer priceOverride,
                            LocalDateTime applyDeadlineAt) {
        this.gathering = gathering;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.maxAttendees = maxAttendees;
        this.priceOverride = priceOverride;
        this.applyDeadlineAt = applyDeadlineAt;
        this.status = GatheringSessionStatus.OPEN;
        if (gathering.getGatheringType() == GatheringType.RANDOM_TABLE) {
            this.tableSizeMin = DEFAULT_TABLE_SIZE_MIN;
            this.tableSizeMax = DEFAULT_TABLE_SIZE_MAX;
        }
    }

    public void changeStatus(GatheringSessionStatus status) {
        this.status = status;
    }

    public void update(Location location, LocalDate eventDate, LocalTime startTime, LocalTime endTime,
                       Integer priceOverride, int maxAttendees, LocalDateTime applyDeadlineAt) {
        this.location = location;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.priceOverride = priceOverride;
        this.maxAttendees = maxAttendees;
        this.applyDeadlineAt = applyDeadlineAt;
    }

    /**
     * 우연한 식탁 매칭 설정. NULL인 값은 매칭 규칙 기본값을 쓴다. 우연한 식탁이 아닌 회차에 값이 오면 400.
     * 최소·최대 인원이 둘 다 있으면 최소 ≤ 최대.
     */
    public void changeMatchingRules(LocalDateTime matchRunAt, Integer autoConfirmGraceMinutes, Integer tableSizeMin,
                                    Integer tableSizeMax, BigDecimal minGroupScore, Integer maxAgeGap) {
        boolean hasValue = matchRunAt != null || autoConfirmGraceMinutes != null || tableSizeMin != null
                || tableSizeMax != null || minGroupScore != null || maxAgeGap != null;
        if (gathering.getGatheringType() != GatheringType.RANDOM_TABLE) {
            if (hasValue) {
                throw new CustomException(ErrorCode.NOT_RANDOM_TABLE_SESSION);
            }
            return;
        }
        if (tableSizeMin != null && tableSizeMax != null && tableSizeMin > tableSizeMax) {
            throw new CustomException(ErrorCode.INVALID_TABLE_SIZE_RANGE);
        }
        this.matchRunAt = matchRunAt;
        this.autoConfirmGraceMinutes = autoConfirmGraceMinutes;
        this.tableSizeMin = tableSizeMin;
        this.tableSizeMax = tableSizeMax;
        this.minGroupScore = minGroupScore;
        this.maxAgeGap = maxAgeGap;
    }

    public Integer getEffectivePrice() {
        return priceOverride != null ? priceOverride : gathering.getBasePrice();
    }

    /** 신청 가능한 회차인지 검사한다. 모집중(날짜 미경과 OPEN)이 아니거나 신청 마감이 지났으면 예외. (KAN-338) */
    public void validateApplicable(LocalDateTime now) {
        if (getEffectiveStatus() != GatheringStatus.OPEN) {
            throw new CustomException(ErrorCode.GATHERING_NOT_RECRUITING);
        }
        if (applyDeadlineAt != null && now.isAfter(applyDeadlineAt)) {
            throw new CustomException(ErrorCode.APPLY_DEADLINE_PASSED);
        }
    }

    /** 회차 시작 시각. 시작 시간이 없으면 행사일 0시. (KAN-342 취소 기한 기준) */
    public LocalDateTime getStartAt() {
        return eventDate.atTime(startTime != null ? startTime : LocalTime.MIDNIGHT);
    }

    /** 종류·회차 API(KAN-338)용 유효 상태. eventDate가 지난 OPEN 회차는 DONE. */
    public GatheringSessionStatus getEffectiveSessionStatus() {
        return GatheringSessionStatus.from(getEffectiveStatus());
    }

    /**
     * 사용자 응답용 유효 상태. eventDate가 지난 모집중(OPEN) 회차는 진행 완료(COMPLETED)로 간주한다. (KAN-163)
     * CANCELLED, CLOSED, DONE 등 명시적 상태는 그대로 유지한다.
     */
    public GatheringStatus getEffectiveStatus() {
        if (status == GatheringSessionStatus.OPEN && eventDate.isBefore(LocalDate.now())) {
            return GatheringStatus.COMPLETED;
        }
        return status.toGatheringStatus();
    }
}
