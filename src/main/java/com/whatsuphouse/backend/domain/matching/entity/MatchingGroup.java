package com.whatsuphouse.backend.domain.matching.entity;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.matching.enums.MatchingGroupStatus;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "matching_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(columnDefinition = "TEXT")
    private String region;

    @Column(name = "group_size", nullable = false)
    private int groupSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchingGroupStatus status = MatchingGroupStatus.PENDING;

    @Column(name = "restaurant_name", columnDefinition = "TEXT")
    private String restaurantName;

    @Column(name = "restaurant_address", columnDefinition = "TEXT")
    private String restaurantAddress;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    @Column(name = "algorithm_version", nullable = false, length = 20)
    private String algorithmVersion = "rule-v1";

    @Column(name = "group_score", precision = 5, scale = 4)
    private BigDecimal groupScore;

    @Builder
    public MatchingGroup(Gathering gathering, LocalDate eventDate, String region, int groupSize,
                         String restaurantName, String restaurantAddress,
                         String algorithmVersion, BigDecimal groupScore) {
        this.gathering = gathering;
        this.eventDate = eventDate;
        this.region = region;
        this.groupSize = groupSize;
        this.restaurantName = restaurantName;
        this.restaurantAddress = restaurantAddress;
        this.algorithmVersion = algorithmVersion != null ? algorithmVersion : "rule-v1";
        this.groupScore = groupScore;
        this.status = MatchingGroupStatus.PENDING;
    }

    public void markMatched(LocalDateTime matchedAt) {
        this.matchedAt = matchedAt;
    }

    public void confirm() {
        this.status = MatchingGroupStatus.CONFIRMED;
    }

    public void updateRestaurant(String restaurantName, String restaurantAddress) {
        this.restaurantName = restaurantName;
        this.restaurantAddress = restaurantAddress;
    }
}
