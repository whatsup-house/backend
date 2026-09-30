package com.whatsuphouse.backend.domain.matching.entity;

import com.whatsuphouse.backend.domain.matching.enums.DiningContentKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 회차 시작 시각에 테이블 채팅방에 올리는 대화 주제·아이스브레이킹. V13 시드로만 채운다(관리 UI 없음). (설계 4.9, KAN-349)
 * interestTag는 표준 폼 INTERESTS 선택지 값. NULL이면 범용.
 */
@Entity
@Table(name = "dining_contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiningContent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiningContentKind kind;

    @Column(name = "interest_tag", length = 100)
    private String interestTag;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    public DiningContent(DiningContentKind kind, String interestTag, String body) {
        this.kind = kind;
        this.interestTag = interestTag;
        this.body = body;
    }
}
