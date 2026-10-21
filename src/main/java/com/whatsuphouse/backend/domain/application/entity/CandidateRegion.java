package com.whatsuphouse.backend.domain.application.entity;

import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 우연한 식탁 희망 지역 1개와 지망 순위(1부터). V31 전에 저장된 행은 순위가 비어 있다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CandidateRegion {

    @Convert(converter = DiningRegion.LabelConverter.class)
    @Column(name = "region", nullable = false, length = 50)
    private DiningRegion region;

    @Column(name = "priority")
    private Integer priority;
}
