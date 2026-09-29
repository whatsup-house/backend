package com.whatsuphouse.backend.domain.gathering.entity;

import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * 모임 종류. 날짜·시간·장소·정원·상태는 회차(GatheringSession)가 가진다. (KAN-337)
 * 같은 제목의 모임은 하나의 종류 아래 여러 회차가 된다.
 */
@Entity
@Table(name = "gatherings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Gathering extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "how_to_run", columnDefinition = "jsonb")
    private List<String> howToRun = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags = List.of();

    // 회차 가격 오버라이드(GatheringSession.priceOverride)가 없을 때의 기본 가격.
    @Column(name = "base_price")
    private Integer basePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "gathering_type", length = 20)
    private GatheringType gatheringType;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "is_curated", nullable = false)
    private boolean isCurated = false;

    @Column(name = "curated_rank", nullable = false)
    private int curatedRank = 0;

    @Builder
    public Gathering(String title, String description, Integer basePrice, String thumbnailUrl,
                     GatheringType gatheringType, List<String> howToRun, List<String> tags) {
        this.title = title;
        this.description = description;
        this.howToRun = howToRun != null ? List.copyOf(howToRun) : List.of();
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.basePrice = basePrice;
        this.thumbnailUrl = thumbnailUrl;
        this.gatheringType = gatheringType != null ? gatheringType : GatheringType.REGULAR;
    }

    public void updateCuration(boolean isCurated) {
        this.isCurated = isCurated;
    }

    public void updateCuratedRank(int rank) {
        this.curatedRank = rank;
    }

    public void update(String title, String description, String thumbnailUrl,
                       List<String> howToRun, List<String> tags) {
        this.title = title;
        this.description = description;
        this.howToRun = howToRun != null ? List.copyOf(howToRun) : List.of();
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.thumbnailUrl = thumbnailUrl;
    }
}
