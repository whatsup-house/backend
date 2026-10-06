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

    // 상세 슬라이더용 사진(썸네일 다음에 노출). 배열 순서 = 노출 순서. (KAN-371)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_urls", nullable = false, columnDefinition = "jsonb")
    private List<String> imageUrls = List.of();

    @Column(name = "is_curated", nullable = false)
    private boolean isCurated = false;

    @Column(name = "curated_rank", nullable = false)
    private int curatedRank = 0;

    // 입금 계좌 정보 (선택). (KAN-390)
    @Column(name = "account_bank", length = 50)
    private String accountBank;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    @Builder
    public Gathering(String title, String description, Integer basePrice, String thumbnailUrl,
                     GatheringType gatheringType, List<String> howToRun, List<String> tags,
                     List<String> imageUrls, String accountBank, String accountNumber,
                     String accountHolder) {
        this.title = title;
        this.description = description;
        this.howToRun = howToRun != null ? List.copyOf(howToRun) : List.of();
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.basePrice = basePrice;
        this.thumbnailUrl = thumbnailUrl;
        this.imageUrls = imageUrls != null ? List.copyOf(imageUrls) : List.of();
        this.gatheringType = gatheringType != null ? gatheringType : GatheringType.REGULAR;
        this.accountBank = accountBank;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }

    public void updateCuration(boolean isCurated) {
        this.isCurated = isCurated;
    }

    public void updateCuratedRank(int rank) {
        this.curatedRank = rank;
    }

    public void update(String title, String description, String thumbnailUrl, List<String> imageUrls,
                       List<String> howToRun, List<String> tags, Integer basePrice,
                       String accountBank, String accountNumber, String accountHolder) {
        this.title = title;
        this.description = description;
        this.howToRun = howToRun != null ? List.copyOf(howToRun) : List.of();
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.thumbnailUrl = thumbnailUrl;
        this.imageUrls = imageUrls != null ? List.copyOf(imageUrls) : List.of();
        this.basePrice = basePrice;
        this.accountBank = accountBank;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }
}
