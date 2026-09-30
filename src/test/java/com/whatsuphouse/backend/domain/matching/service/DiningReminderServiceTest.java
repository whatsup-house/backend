package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.matching.entity.DiningContent;
import com.whatsuphouse.backend.domain.matching.enums.DiningContentKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 대화 콘텐츠 선택 규칙(설계 4.9). */
class DiningReminderServiceTest {

    private final List<DiningContent> contents = List.of(
            topic("여행", "여행 1"), topic("여행", "여행 2"),
            topic("음악", "음악 1"),
            topic("영화·드라마", "영화 1"),
            topic(null, "범용 1"), topic(null, "범용 2"), topic(null, "범용 3"),
            new DiningContent(DiningContentKind.ICEBREAKER, null, "아이스 1"),
            new DiningContent(DiningContentKind.ICEBREAKER, null, "아이스 2"));

    @Test
    @DisplayName("두 명 이상 겹친 관심사 주제를 많이 겹친 순으로 태그당 하나씩 먼저, 모자라면 범용으로 채우고 아이스브레이킹 1개를 붙인다")
    void pickContents_commonInterestsFirst() {
        // given: 여행 3명, 음악 2명, 영화·드라마 1명(공통 아님)
        List<Set<String>> interests = List.of(
                Set.of("여행", "음악"), Set.of("여행", "음악"), Set.of("여행", "영화·드라마"), Set.of());

        // when
        List<DiningContent> picked = DiningReminderService.pickContents(contents, interests, new Random(1));

        // then
        assertThat(picked).hasSize(4);
        assertThat(picked.subList(0, 3)).extracting(DiningContent::getKind).containsOnly(DiningContentKind.TOPIC);
        assertThat(picked.get(0).getInterestTag()).isEqualTo("여행");
        assertThat(picked.get(1).getInterestTag()).isEqualTo("음악");
        assertThat(picked.get(2).getInterestTag()).isNull();
        assertThat(picked.get(3).getKind()).isEqualTo(DiningContentKind.ICEBREAKER);
    }

    @Test
    @DisplayName("공통 관심사가 없으면 범용 주제 3개 + 아이스브레이킹")
    void pickContents_noCommonInterest_usesGeneric() {
        // when
        List<DiningContent> picked = DiningReminderService.pickContents(contents,
                List.of(Set.of("여행"), Set.of("음악")), new Random(1));

        // then
        assertThat(picked.subList(0, 3)).allSatisfy(content -> {
            assertThat(content.getKind()).isEqualTo(DiningContentKind.TOPIC);
            assertThat(content.getInterestTag()).isNull();
        });
        assertThat(picked.get(3).getKind()).isEqualTo(DiningContentKind.ICEBREAKER);
    }

    private static DiningContent topic(String tag, String body) {
        return new DiningContent(DiningContentKind.TOPIC, tag, body);
    }
}
