package com.whatsuphouse.backend.domain.feed.entity;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 인스타그램 게시물·릴스를 옮겨 담은 피드 게시물. (KAN-380) */
@Entity
@Table(
        name = "feed_posts",
        indexes = {
                @Index(name = "idx_feed_posts_visible_posted_at", columnList = "is_visible, posted_at DESC")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 배열 순서 = 노출 순서.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media", nullable = false, columnDefinition = "jsonb")
    private List<FeedMedia> media = List.of();

    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    // 인스타그램 게시 시각. 피드 정렬 기준.
    @Column(name = "posted_at", nullable = false)
    private LocalDateTime postedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id")
    private Gathering gathering;

    @Column(name = "is_visible", nullable = false)
    private boolean isVisible = false;

    @Builder
    public FeedPost(List<FeedMedia> media, String caption, String instagramUrl, LocalDateTime postedAt,
                    Gathering gathering, boolean isVisible) {
        update(media, caption, instagramUrl, postedAt, gathering);
        this.isVisible = isVisible;
    }

    public void update(List<FeedMedia> media, String caption, String instagramUrl, LocalDateTime postedAt,
                       Gathering gathering) {
        this.media = media != null ? List.copyOf(media) : List.of();
        this.caption = caption;
        this.instagramUrl = instagramUrl;
        this.postedAt = postedAt;
        this.gathering = gathering;
    }

    public void changeVisibility(boolean isVisible) {
        this.isVisible = isVisible;
    }
}
