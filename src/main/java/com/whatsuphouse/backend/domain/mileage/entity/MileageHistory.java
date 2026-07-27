package com.whatsuphouse.backend.domain.mileage.entity;

import com.whatsuphouse.backend.domain.mileage.enums.MileageType;
import com.whatsuphouse.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mileage_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MileageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mileage_user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "mileage_type", nullable = false, length = 20)
    private MileageType type;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "related_id")
    private UUID relatedId;

    @Column(name = "adjust_reason", length = 255)
    private String adjustReason;

    @Column(name = "earned_date", nullable = false, updatable = false)
    private LocalDateTime earnedDate;

    @Builder
    public MileageHistory(User user, MileageType type, Integer amount, Integer balanceAfter, UUID relatedId, String adjustReason) {
        this.user = user;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.relatedId = relatedId;
        this.adjustReason = adjustReason;
    }

    @PrePersist
    void prePersist() {
        if (earnedDate == null) {
            earnedDate = LocalDateTime.now();
        }
    }
}
