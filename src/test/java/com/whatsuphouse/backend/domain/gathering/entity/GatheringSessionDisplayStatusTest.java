package com.whatsuphouse.backend.domain.gathering.entity;

import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GatheringSessionDisplayStatusTest {

    private static GatheringSession session(LocalDate eventDate, LocalDateTime applyDeadlineAt) {
        return GatheringSession.builder().gathering(Gathering.builder().title("우연한 식탁").build()).eventDate(eventDate).maxAttendees(6).applyDeadlineAt(applyDeadlineAt).build();
    }

    @Test
    @DisplayName("신청 마감이 지난 모집중 회차는 마감으로 보인다")
    void deadlinePassed_closed() {
        GatheringSession s = session(LocalDate.now().plusDays(1), LocalDateTime.now().minusMinutes(1));
        assertThat(s.getEffectiveStatus()).isEqualTo(GatheringStatus.OPEN);
        assertThat(s.getDisplayStatus()).isEqualTo(GatheringStatus.CLOSED);
    }

    @Test
    @DisplayName("마감 전이거나 마감이 없으면 모집중, 지난 회차는 진행 완료 그대로")
    void otherwise_effective() {
        assertThat(session(LocalDate.now().plusDays(1), LocalDateTime.now().plusHours(1)).getDisplayStatus()).isEqualTo(GatheringStatus.OPEN);
        assertThat(session(LocalDate.now().plusDays(1), null).getDisplayStatus()).isEqualTo(GatheringStatus.OPEN);
        assertThat(session(LocalDate.now().minusDays(1), LocalDateTime.now().minusDays(3)).getDisplayStatus()).isEqualTo(GatheringStatus.COMPLETED);
    }
}
