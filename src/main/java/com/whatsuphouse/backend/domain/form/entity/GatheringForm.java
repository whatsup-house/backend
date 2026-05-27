package com.whatsuphouse.backend.domain.form.entity;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "gathering_forms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatheringForm extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder
    public GatheringForm(Gathering gathering, String description, boolean isActive) {
        this.gathering = gathering;
        this.description = description;
        this.isActive = isActive;
    }
}
