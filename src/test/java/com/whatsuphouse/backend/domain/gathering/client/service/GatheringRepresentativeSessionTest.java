package com.whatsuphouse.backend.domain.gathering.client.service;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatheringRepresentativeSessionTest {

    private static final Gathering GATHERING = Gathering.builder().title("우연한 식탁").build();

    private static GatheringSession session(int daysFromToday, LocalDateTime applyDeadlineAt) {
        return GatheringSession.builder().gathering(GATHERING).eventDate(LocalDate.now().plusDays(daysFromToday))
                .maxAttendees(6).applyDeadlineAt(applyDeadlineAt).build();
    }

    @Test
    @DisplayName("가장 가까운 회차가 신청 마감이면 다음 모집중 회차를 대표로 고른다")
    void nearestClosed_picksNextOpen() {
        GatheringSession closedSoon = session(1, LocalDateTime.now().minusHours(1));
        GatheringSession openLater = session(15, LocalDateTime.now().plusDays(13));

        assertThat(GatheringService.pickRepresentative(List.of(closedSoon, openLater))).contains(openLater);
    }

    @Test
    @DisplayName("모집중 회차가 없으면 오늘 이후 가장 이른 회차")
    void noOpen_picksNearestUpcoming() {
        GatheringSession closedSoon = session(1, LocalDateTime.now().minusHours(1));
        GatheringSession closedLater = session(8, LocalDateTime.now().minusMinutes(1));

        assertThat(GatheringService.pickRepresentative(List.of(closedLater, closedSoon))).contains(closedSoon);
    }
}
