package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.dining.admin.dto.request.SessionVenueRequest;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.SessionVenueResponse;
import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.repository.SessionVenueRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.entity.MatchingGroup;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminVenueServiceTest {

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private SessionVenueRepository sessionVenueRepository;

    @Mock
    private GatheringService gatheringService;

    @Mock
    private MatchingService matchingService;

    @InjectMocks
    private AdminVenueService adminVenueService;

    private UUID sessionId;
    private MatchingGroup table;

    @BeforeEach
    void setUp() {
        Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        GatheringSession session = GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().plusDays(7)).maxAttendees(12).build();
        sessionId = UUID.randomUUID();
        ReflectionTestUtils.setField(session, "id", sessionId);
        table = MatchingGroup.builder().session(session).eventDate(session.getEventDate()).groupSize(4).build();
        ReflectionTestUtils.setField(table, "id", UUID.randomUUID());
    }

    private Venue venue(boolean isActive) {
        Venue venue = Venue.builder().name("을지로 한식당").address("서울 중구").region("을지로").isActive(isActive).build();
        ReflectionTestUtils.setField(venue, "id", UUID.randomUUID());
        return venue;
    }

    private SessionVenue sessionVenue(Venue venue, int capacity, int used) {
        SessionVenue sessionVenue = new SessionVenue(sessionId, venue.getId(), capacity);
        ReflectionTestUtils.setField(sessionVenue, "usedTables", used);
        return sessionVenue;
    }

    // ── 회차 식당 풀 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("배정된 테이블 수보다 수용 수를 줄이면 409")
    void updateSessionVenues_belowUsed_throws() {
        Venue venue = venue(true);
        given(venueRepository.findAllById(any())).willReturn(List.of(venue));
        given(sessionVenueRepository.findBySessionIdForUpdate(sessionId)).willReturn(List.of(sessionVenue(venue, 3, 2)));

        assertThatThrownBy(() -> adminVenueService.updateSessionVenues(sessionId,
                List.of(new SessionVenueRequest(venue.getId(), 1))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VENUE_CAPACITY_EXCEEDED);
    }

    @Test
    @DisplayName("배정된 테이블이 있는 식당을 풀에서 빼면 409")
    void updateSessionVenues_removeInUse_throws() {
        Venue venue = venue(true);
        given(venueRepository.findAllById(any())).willReturn(List.of());
        given(sessionVenueRepository.findBySessionIdForUpdate(sessionId)).willReturn(List.of(sessionVenue(venue, 2, 1)));

        assertThatThrownBy(() -> adminVenueService.updateSessionVenues(sessionId, List.of()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VENUE_CAPACITY_EXCEEDED);
        then(sessionVenueRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("비활성 식당을 풀에 새로 넣으면 400")
    void updateSessionVenues_newInactive_throws() {
        Venue venue = venue(false);
        given(venueRepository.findAllById(any())).willReturn(List.of(venue));
        given(sessionVenueRepository.findBySessionIdForUpdate(sessionId)).willReturn(List.of());

        assertThatThrownBy(() -> adminVenueService.updateSessionVenues(sessionId,
                List.of(new SessionVenueRequest(venue.getId(), 2))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VENUE_INACTIVE);
        then(sessionVenueRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 풀에 있던 식당은 비활성이 됐어도 수용 수를 바꿀 수 있다(배정 중이라 뺄 수도 없는 경우)")
    void updateSessionVenues_existingInactive_keepsAndUpdates() {
        Venue venue = venue(false);
        SessionVenue existing = sessionVenue(venue, 2, 1);
        given(venueRepository.findAllById(any())).willReturn(List.of(venue));
        given(sessionVenueRepository.findBySessionIdForUpdate(sessionId)).willReturn(List.of(existing));

        List<SessionVenueResponse> result = adminVenueService.updateSessionVenues(sessionId,
                List.of(new SessionVenueRequest(venue.getId(), 3)));

        assertThat(existing.getCapacityTables()).isEqualTo(3);
        assertThat(result).singleElement().satisfies(response -> assertThat(response.getUsedTables()).isEqualTo(1));
    }

    @Test
    @DisplayName("같은 식당을 두 번 넣으면 400")
    void updateSessionVenues_duplicate_throws() {
        UUID venueId = UUID.randomUUID();

        assertThatThrownBy(() -> adminVenueService.updateSessionVenues(sessionId,
                List.of(new SessionVenueRequest(venueId, 1), new SessionVenueRequest(venueId, 2))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_SESSION_VENUE);
    }

    // ── 테이블 식당 배정 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("비활성 식당을 테이블에 배정하면 400")
    void assignTableVenue_inactive_throws() {
        Venue venue = venue(false);
        given(venueRepository.findByIdAndDeletedAtIsNull(venue.getId())).willReturn(Optional.of(venue));

        assertThatThrownBy(() -> adminVenueService.assignTableVenue(table.getId(), venue.getId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VENUE_INACTIVE);
        then(matchingService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("수용 테이블이 가득 찬 식당을 배정하면 409이고 테이블 식당은 그대로다")
    void assignTableVenue_full_throws() {
        Venue venue = venue(true);
        given(venueRepository.findByIdAndDeletedAtIsNull(venue.getId())).willReturn(Optional.of(venue));
        given(matchingService.findGroup(table.getId())).willReturn(table);
        given(sessionVenueRepository.findBySessionIdAndVenueIdForUpdate(sessionId, venue.getId()))
                .willReturn(Optional.of(sessionVenue(venue, 1, 1)));

        assertThatThrownBy(() -> adminVenueService.assignTableVenue(table.getId(), venue.getId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VENUE_CAPACITY_EXCEEDED);
        assertThat(table.getVenueId()).isNull();
    }

    @Test
    @DisplayName("회차 식당 풀에 없는 식당을 배정하면 400")
    void assignTableVenue_notInSession_throws() {
        Venue venue = venue(true);
        given(venueRepository.findByIdAndDeletedAtIsNull(venue.getId())).willReturn(Optional.of(venue));
        given(matchingService.findGroup(table.getId())).willReturn(table);
        given(sessionVenueRepository.findBySessionIdAndVenueIdForUpdate(sessionId, venue.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> adminVenueService.assignTableVenue(table.getId(), venue.getId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VENUE_NOT_IN_SESSION);
    }

    @Test
    @DisplayName("식당을 바꾸면 새 식당 used_tables는 1 늘고 이전 식당은 1 줄어든다")
    void assignTableVenue_movesUsage() {
        Venue previous = venue(true);
        Venue target = venue(true);
        SessionVenue previousUsage = sessionVenue(previous, 1, 1);
        SessionVenue targetUsage = sessionVenue(target, 2, 0);
        table.assignVenue(previous.getId());
        given(venueRepository.findByIdAndDeletedAtIsNull(target.getId())).willReturn(Optional.of(target));
        given(matchingService.findGroup(table.getId())).willReturn(table);
        given(sessionVenueRepository.findBySessionIdAndVenueIdForUpdate(sessionId, target.getId()))
                .willReturn(Optional.of(targetUsage));
        given(sessionVenueRepository.findBySessionIdAndVenueIdForUpdate(sessionId, previous.getId()))
                .willReturn(Optional.of(previousUsage));

        adminVenueService.assignTableVenue(table.getId(), target.getId());

        assertThat(table.getVenueId()).isEqualTo(target.getId());
        assertThat(targetUsage.getUsedTables()).isEqualTo(1);
        assertThat(previousUsage.getUsedTables()).isZero();
    }
}
