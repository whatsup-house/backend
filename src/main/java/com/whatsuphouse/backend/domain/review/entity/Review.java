package com.whatsuphouse.backend.domain.review.entity;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.review.enums.ReviewType;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reviews_application_id", columnNames = "application_id")
        },
        indexes = {
                @Index(name = "idx_reviews_gathering_id", columnList = "gathering_id"),
                @Index(name = "idx_reviews_user_id", columnList = "user_id"),
                @Index(name = "idx_reviews_home_featured", columnList = "is_home_featured, home_display_order")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 10)
    private ReviewType reviewType = ReviewType.TEXT;

    @Column(name = "review_content", nullable = false, columnDefinition = "TEXT")
    private String reviewContent;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;

    @Column(name = "is_home_featured", nullable = false)
    private boolean isHomeFeatured = false;

    @Column(name = "home_display_order", nullable = false)
    private Integer homeDisplayOrder = 0;

    // 마지막으로 좋아요 마일스톤 알림을 발행한 값. 같은 마일스톤 재교차 시 중복 발행을 막는다. (KAN-263)
    @Column(name = "notified_like_milestone", nullable = false)
    private Integer notifiedLikeMilestone = 0;

    @Builder
    public Review(User user, Application application, Gathering gathering, ReviewType reviewType,
                  String reviewContent) {
        this.user = user;
        this.application = application;
        this.gathering = gathering;
        this.reviewType = reviewType == null ? ReviewType.TEXT : reviewType;
        this.reviewContent = reviewContent;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void markLikeMilestoneNotified(int milestone) {
        this.notifiedLikeMilestone = milestone;
    }

    public void updateHomeFeatured(boolean isHomeFeatured, Integer homeDisplayOrder) {
        this.isHomeFeatured = isHomeFeatured;
        this.homeDisplayOrder = homeDisplayOrder == null ? 0 : homeDisplayOrder;
    }

    public void update(String reviewContent, ReviewType reviewType) {
        this.reviewContent = reviewContent;
        this.reviewType = reviewType == null ? ReviewType.TEXT : reviewType;
    }
}
