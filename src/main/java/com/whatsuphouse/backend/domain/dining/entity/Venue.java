package com.whatsuphouse.backend.domain.dining.entity;

import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** 우연한 식탁 식당 풀. 회차별 수용 테이블 수는 SessionVenue. (설계 2.5) */
@Entity
@Table(name = "venues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "map_url", length = 500)
    private String mapUrl;

    @Column(name = "price_range", length = 50)
    private String priceRange;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder
    public Venue(String name, String address, String mapUrl, String priceRange, String region, boolean isActive) {
        update(name, address, mapUrl, priceRange, region, isActive);
    }

    public void update(String name, String address, String mapUrl, String priceRange, String region, boolean isActive) {
        this.name = name;
        this.address = address;
        this.mapUrl = mapUrl;
        this.priceRange = priceRange;
        this.region = region;
        this.isActive = isActive;
    }

    /** 새로 배정할 수 있는지. 삭제된 식당도 비활성으로 본다. */
    public boolean isAssignable() {
        return isActive && getDeletedAt() == null;
    }
}
