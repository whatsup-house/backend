package com.whatsuphouse.backend.domain.matching.entity;

import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 우연한 식탁 테이블(옛 matching_groups, V9에서 개명). 회차 단위 매칭 실행이 만든다. (설계 2.4) */
@Entity
@Table(name = "dining_tables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiningTable extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GatheringSession session;

    // 이 테이블을 만든 매칭 실행(match_runs.id). v1 시절 테이블은 NULL.
    @Column(name = "match_run_id")
    private UUID matchRunId;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(columnDefinition = "TEXT")
    private String region;

    @Column(name = "group_size", nullable = false)
    private int groupSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiningTableStatus status = DiningTableStatus.PROPOSED;

    @Column(name = "restaurant_name", columnDefinition = "TEXT")
    private String restaurantName;

    @Column(name = "restaurant_address", columnDefinition = "TEXT")
    private String restaurantAddress;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    @Column(name = "algorithm_version", nullable = false, length = 20)
    private String algorithmVersion;

    @Column(name = "group_score", precision = 5, scale = 4)
    private BigDecimal groupScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_detail", columnDefinition = "jsonb")
    private ScoreDetail scoreDetail;

    // 유예가 끝나 자동 확정될 시각. 확정 스케줄러(KAN-346)가 읽는다.
    @Column(name = "confirm_at")
    private LocalDateTime confirmAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // 배정 식당(venues.id). 운영자 어드민에서 지정한다. (KAN-348)
    @Column(name = "venue_id")
    private UUID venueId;

    @Column(name = "chat_room_id")
    private UUID chatRoomId;

    // 수동 조정 이력. 수동 조정 재설계(KAN-347)가 쓴다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reallocation_log", columnDefinition = "jsonb")
    private List<Map<String, Object>> reallocationLog;

    // true면 재실행해도 해체하지 않는다(수동 조정 보존).
    @Column(nullable = false)
    private boolean locked = false;

    @Builder
    public DiningTable(GatheringSession session, UUID matchRunId, LocalDate eventDate, String region, int groupSize,
                       String algorithmVersion, BigDecimal groupScore, ScoreDetail scoreDetail,
                       LocalDateTime confirmAt) {
        this.session = session;
        this.matchRunId = matchRunId;
        this.eventDate = eventDate;
        this.region = region;
        this.groupSize = groupSize;
        this.algorithmVersion = algorithmVersion;
        this.groupScore = groupScore;
        this.scoreDetail = scoreDetail;
        this.confirmAt = confirmAt;
        this.status = DiningTableStatus.PROPOSED;
        this.matchedAt = LocalDateTime.now();
    }

    /** PROPOSED 또는 CONFIRMED. 한 신청은 활성 테이블 하나에만 앉는다. */
    public boolean isActive() {
        return status == DiningTableStatus.PROPOSED || status == DiningTableStatus.CONFIRMED;
    }

    public void updateGroupSize(int groupSize) {
        this.groupSize = groupSize;
    }

    public void updateScore(BigDecimal groupScore, ScoreDetail scoreDetail) {
        this.groupScore = groupScore;
        this.scoreDetail = scoreDetail;
    }

    public void confirm() {
        this.status = DiningTableStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    // 해체하면 식당 배정도 푼다. 회차 식당 사용 수(session_venues.used_tables)는 서비스가 내린다.
    public void dissolve() {
        this.status = DiningTableStatus.DISSOLVED;
        this.venueId = null;
    }

    public void assignVenue(UUID venueId) {
        this.venueId = venueId;
    }

    public void updateRestaurant(String restaurantName, String restaurantAddress) {
        this.restaurantName = restaurantName;
        this.restaurantAddress = restaurantAddress;
    }

    // 수동 조정한 테이블은 재실행해도 해체하지 않는다.
    public void lock() {
        this.locked = true;
    }

    /** 조정 이력 1건을 reallocation_log에 덧붙인다. by는 조작한 관리자(시스템 재조정이면 null). (KAN-347) */
    public void recordReallocation(String action, UUID by, String reason, List<UUID> memberIds) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("at", LocalDateTime.now().toString());
        entry.put("action", action);
        entry.put("by", by != null ? by.toString() : null);
        entry.put("reason", reason);
        entry.put("memberIds", memberIds.stream().map(UUID::toString).toList());
        List<Map<String, Object>> log = reallocationLog != null ? new ArrayList<>(reallocationLog) : new ArrayList<>();
        log.add(entry);
        this.reallocationLog = log;
    }

    // 관리자 즉시 확정: 유예를 끝내고 확정 파이프라인이 바로 집어 가게 한다. (KAN-346)
    public void changeConfirmAt(LocalDateTime confirmAt) {
        this.confirmAt = confirmAt;
    }

    public void linkChatRoom(UUID chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    /** 표시용 지역. 테이블 값이 없으면 회차 장소 이름, 둘 다 없으면 null. */
    public String getDisplayRegion() {
        if (region != null && !region.isBlank()) {
            return region;
        }
        return session.getLocation() != null ? session.getLocation().getName() : null;
    }
}
