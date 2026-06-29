package com.whatsuphouse.backend.domain.carousel.entity;

import com.whatsuphouse.backend.domain.carousel.enums.SlideType;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "carousel_slides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarouselSlide extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlideType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String content;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id")
    private Gathering gathering;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = false;

    @Builder
    public CarouselSlide(SlideType type, String title, String content, String imageUrl,
                         Gathering gathering, int sortOrder, boolean isActive) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.gathering = gathering;
        this.sortOrder = sortOrder;
        this.isActive = isActive;
    }

    public void update(SlideType type, String title, String content, String imageUrl,
                       Gathering gathering, int sortOrder) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.gathering = gathering;
        this.sortOrder = sortOrder;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

}
