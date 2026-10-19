package com.whatsuphouse.backend.domain.dining.client.service;

import com.whatsuphouse.backend.domain.dining.client.dto.response.DiningRecommendationResponse;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.enums.LocationStatus;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/** 다음 추천 회차(RET-02): OPEN·마감 전·노출 가능 회차 3개, 지난 참석 지역 -> 같은 요일 -> 날짜 순. (KAN-407) */
@ExtendWith(MockitoExtension.class)
class DiningRecommendationTest {

    @Mock
    private MatchingService matchingService;
    @Mock
    private DiningAttendanceService diningAttendanceService;
    @Mock
    private GatheringService gatheringService;

    @InjectMocks
    private DiningService diningService;

    private final Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
    private final Location gangnam = location("강남");
    private final Location hongdae = location("홍대");
    private final UserPrincipal viewer = new UserPrincipal(UUID.randomUUID(), "u@example.com", false);

    // 지난주에 강남에서 참석한 사용자. 오늘 기준 +1일부터 회차가 열려 있다.
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gathering, "id", UUID.randomUUID());
        GatheringSession past = session(gangnam, LocalDate.now().minusDays(7));
        DiningTable table = DiningTable.builder().session(past).eventDate(past.getEventDate()).groupSize(4)
                .algorithmVersion("rule-v2").build();
        DiningTableMember member = DiningTableMember.builder().table(table).seatOrder(1)
                .assignReason(AssignReason.INITIAL).build();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        given(matchingService.listUserTableMembers(any(), any())).willReturn(List.of(member));
        given(diningAttendanceService.findAttendanceStatuses(any()))
                .willReturn(Map.of(member.getId(), AttendanceStatus.ATTENDED));
        given(gatheringService.visibleTo(viewer)).willReturn((Predicate<Gathering>) g -> true);
    }

    @Test
    @DisplayName("지난 참석 지역이 같은 회차가 먼저, 그다음 같은 요일, 그다음 날짜 순이고 최대 3개다")
    void recommendations_prefersPastRegion() {
        LocalDate base = LocalDate.now().plusDays(1);
        GatheringSession hongdaeSoon = session(hongdae, base);
        GatheringSession gangnamLater = session(gangnam, base.plusDays(10));
        GatheringSession gangnamSoon = session(gangnam, base.plusDays(3));
        GatheringSession hongdaeSameWeekday = session(hongdae, nextSameWeekdayAsPast(base.plusDays(20)));
        GatheringSession hongdaeLast = session(hongdae, base.plusDays(30));
        given(gatheringService.listUpcomingSessions(GatheringType.RANDOM_TABLE))
                .willReturn(List.of(hongdaeSoon, gangnamLater, gangnamSoon, hongdaeSameWeekday, hongdaeLast));

        List<DiningRecommendationResponse> result = diningService.listMyRecommendations(viewer);

        assertThat(result).extracting(DiningRecommendationResponse::getSessionId)
                .hasSize(3)
                .containsExactly(gangnamSoon.getId(), gangnamLater.getId(), hongdaeSameWeekday.getId())
                .doesNotContain(hongdaeSoon.getId());
    }

    @Test
    @DisplayName("마감됐거나 모집 중이 아닌 회차와 노출 규칙에 걸리는 회차는 추천하지 않는다")
    void recommendations_excludesClosedAndInvisible() {
        LocalDate base = LocalDate.now().plusDays(2);
        GatheringSession open = session(gangnam, base);
        GatheringSession deadlinePassed = session(gangnam, base.plusDays(1));
        ReflectionTestUtils.setField(deadlinePassed, "applyDeadlineAt", LocalDateTime.now().minusHours(1));
        GatheringSession closed = session(gangnam, base.plusDays(2));
        closed.changeStatus(GatheringSessionStatus.CLOSED);
        Gathering hiddenKind = Gathering.builder().title("숨김").gatheringType(GatheringType.RANDOM_TABLE).build();
        ReflectionTestUtils.setField(hiddenKind, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(hiddenKind, "hidden", true);
        GatheringSession hidden = GatheringSession.builder()
                .gathering(hiddenKind).location(gangnam).eventDate(base.plusDays(3)).maxAttendees(12).build();
        ReflectionTestUtils.setField(hidden, "id", UUID.randomUUID());
        given(gatheringService.visibleTo(viewer)).willReturn((Predicate<Gathering>) g -> !g.isHidden());
        given(gatheringService.listUpcomingSessions(GatheringType.RANDOM_TABLE))
                .willReturn(List.of(open, deadlinePassed, closed, hidden));

        List<DiningRecommendationResponse> result = diningService.listMyRecommendations(viewer);

        assertThat(result).extracting(DiningRecommendationResponse::getSessionId).containsExactly(open.getId());
    }

    private DayOfWeek pastWeekday() {
        return LocalDate.now().minusDays(7).getDayOfWeek();
    }

    private LocalDate nextSameWeekdayAsPast(LocalDate from) {
        LocalDate date = from;
        while (date.getDayOfWeek() != pastWeekday()) {
            date = date.plusDays(1);
        }
        return date;
    }

    private GatheringSession session(Location location, LocalDate date) {
        GatheringSession session = GatheringSession.builder()
                .gathering(gathering).location(location).eventDate(date).maxAttendees(12).build();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    private static Location location(String name) {
        return Location.builder().name(name).address("서울").status(LocationStatus.ACTIVE).maxCapacity(20).build();
    }
}
