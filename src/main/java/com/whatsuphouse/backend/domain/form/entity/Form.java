package com.whatsuphouse.backend.domain.form.entity;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "forms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Form extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 템플릿(is_template=true)은 게더링이 없으므로 nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id")
    private Gathering gathering;

    @Column(name = "is_template", nullable = false)
    private boolean isTemplate = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "gathering_type", length = 20)
    private GatheringType gatheringType;

    @Column(name = "guide_text", columnDefinition = "TEXT")
    private String guideText;

    @Builder
    public Form(Gathering gathering, boolean isTemplate, GatheringType gatheringType, String guideText) {
        this.gathering = gathering;
        this.isTemplate = isTemplate;
        this.gatheringType = gatheringType;
        this.guideText = guideText;
    }

    public void updateGuideText(String guideText) {
        this.guideText = guideText;
    }
}
