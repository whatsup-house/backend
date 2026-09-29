package com.whatsuphouse.backend.domain.gathering.service;

import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.gathering.client.dto.response.CuratedGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringSessionResponse;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.enums.LocationStatus;
import com.whatsuphouse.backend.global.common.enums.AppLocale;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GatheringServiceTest {

    @Mock
    private GatheringRepository gatheringRepository;

    @Mock
    private GatheringSessionRepository gatheringSessionRepository;

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private GatheringService gatheringService;

    private UUID gatheringId;
    private UUID sessionId;
    private Gathering gathering;
    private GatheringSession session;
    private LocalDate eventDate;

    @BeforeEach
    void setUp() {
        gatheringId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        eventDate = LocalDate.now().plusDays(7);

        gathering = Gathering.builder()
                .title("재즈 게더링")
                .basePrice(15000)
                .build();
        ReflectionTestUtils.setField(gathering, "id", gatheringId);
        session = buildSession(gathering, eventDate);
        ReflectionTestUtils.setField(session, "id", sessionId);
    }

    // ── listGatherings() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("필터 없이 조회하면 다가오는 회차를 종류 단위로 묶고, 종류는 가장 이른 회차 순이다")
    void listGatherings_noFilter_groupsUpcomingSessionsByKind() {
        // given
        Gathering other = Gathering.builder().title("보드게임").build();
        ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
        GatheringSession otherSoon = withId(buildSession(other, LocalDate.now().plusDays(1)));
        GatheringSession later = withId(buildSession(gathering, LocalDate.now().plusDays(14)));
        given(gatheringSessionRepository.findByEventDateGreaterThanEqualAndDeletedAtIsNull(LocalDate.now()))
                .willReturn(List.of(later, session, otherSoon));

        // when
        List<GatheringResponse> result = gatheringService.listGatherings(null, null);

        // then
        assertThat(result).extracting(GatheringResponse::getId).containsExactly(other.getId(), gatheringId);
        assertThat(result.get(1).getTitle()).isEqualTo("재즈 게더링");
        assertThat(result.get(1).getBasePrice()).isEqualTo(15000);
        assertThat(result.get(1).getSessions()).extracting(GatheringSessionResponse::getId)
                .containsExactly(sessionId, later.getId());
    }

    @Test
    @DisplayName("날짜 필터는 그 날짜 회차가 있는 종류만, 그 회차만 붙여 반환")
    void listGatherings_byDate_returnsKindsWithSessionsOnDate() {
        // given
        given(gatheringSessionRepository.findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(eventDate))
                .willReturn(List.of(session));

        // when
        List<GatheringResponse> result = gatheringService.listGatherings(eventDate, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSessions()).extracting(GatheringSessionResponse::getEventDate)
                .containsExactly(eventDate);
    }

    @Test
    @DisplayName("status=OPEN은 유효 상태로 거른다 — 날짜가 지난 OPEN 회차는 빠진다 (KAN-163)")
    void listGatherings_openStatus_excludesPastOpenSession() {
        // given
        GatheringSession pastOpen = withId(pastSession(GatheringSessionStatus.OPEN));
        given(gatheringSessionRepository.findByDeletedAtIsNull()).willReturn(List.of(session, pastOpen));

        // when
        List<GatheringResponse> result = gatheringService.listGatherings(null, GatheringSessionStatus.OPEN);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSessions()).extracting(GatheringSessionResponse::getId).containsExactly(sessionId);
    }

    @Test
    @DisplayName("status=DONE이면 날짜가 지난 OPEN 회차도 DONE으로 포함된다")
    void listGatherings_doneStatus_includesPastOpenSession() {
        // given
        GatheringSession pastOpen = withId(pastSession(GatheringSessionStatus.OPEN));
        given(gatheringSessionRepository.findByDeletedAtIsNull()).willReturn(List.of(session, pastOpen));

        // when
        List<GatheringResponse> result = gatheringService.listGatherings(null, GatheringSessionStatus.DONE);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSessions()).extracting(GatheringSessionResponse::getStatus)
                .containsExactly(GatheringSessionStatus.DONE);
    }

    // ── getGathering() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("종류 상세는 전체 회차를 날짜·시간 순으로, 가격 오버라이드와 정원 차지 인원을 반영해 반환")
    void getGathering_returnsKindWithSessions() {
        // given
        GatheringSession overridden = withId(GatheringSession.builder()
                .gathering(gathering).eventDate(eventDate).startTime(LocalTime.of(10, 0))
                .maxAttendees(10).priceOverride(20000).build());
        ReflectionTestUtils.setField(session, "startTime", LocalTime.of(19, 0));
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)))
                .willReturn(List.of(session, overridden));
        given(applicationService.countSeatsBySessionIds(anyList())).willReturn(Map.of(sessionId, 3L));

        // when
        GatheringDetailResponse response = gatheringService.getGathering(gatheringId);

        // then
        assertThat(response.getId()).isEqualTo(gatheringId);
        assertThat(response.getBasePrice()).isEqualTo(15000);
        assertThat(response.getSessions()).extracting(GatheringSessionResponse::getId)
                .containsExactly(overridden.getId(), sessionId);
        assertThat(response.getSessions()).extracting(GatheringSessionResponse::getPrice).containsExactly(20000, 15000);
        assertThat(response.getSessions()).extracting(GatheringSessionResponse::getConfirmedCount).containsExactly(0L, 3L);
    }

    @Test
    @DisplayName("옛 회차 ID로 상세를 요청하면 그 회차가 속한 종류로 응답한다")
    void getGathering_legacySessionId_returnsOwningKind() {
        // given
        given(gatheringRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.empty());
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)))
                .willReturn(List.of(session));

        // when
        GatheringDetailResponse response = gatheringService.getGathering(sessionId);

        // then
        assertThat(response.getId()).isEqualTo(gatheringId);
        assertThat(response.getSessions()).extracting(GatheringSessionResponse::getId).containsExactly(sessionId);
    }

    @Test
    @DisplayName("회차 location에 네이버·카카오 지도 URL이 포함된다")
    void getGathering_includesLocationProviderMapUrls() {
        // given
        Location location = Location.builder()
                .name("재즈바 A")
                .address("서울시 마포구 합정동 123")
                .naverMapUrl("https://naver.me/abcd1234")
                .kakaoMapUrl("https://kko.kakao.com/xyz789")
                .status(LocationStatus.ACTIVE)
                .maxCapacity(20)
                .build();
        ReflectionTestUtils.setField(session, "location", location);
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)))
                .willReturn(List.of(session));

        // when
        GatheringDetailResponse response = gatheringService.getGathering(gatheringId);

        // then
        GatheringSessionResponse.LocationDetail detail = response.getSessions().get(0).getLocation();
        assertThat(detail.getNaverMapUrl()).isEqualTo("https://naver.me/abcd1234");
        assertThat(detail.getKakaoMapUrl()).isEqualTo("https://kko.kakao.com/xyz789");
    }

    @Test
    @DisplayName("종류도 회차도 없는 ID면 GATHERING_NOT_FOUND")
    void getGathering_notFound_throwsException() {
        // given
        given(gatheringRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.empty());
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gatheringService.getGathering(sessionId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_FOUND);
    }

    @Test
    @DisplayName("날짜가 지난 OPEN 회차는 DONE, 취소 회차는 CANCELLED 그대로 (KAN-163)")
    void getGathering_pastSessions_useEffectiveStatus() {
        // given
        GatheringSession pastOpen = withId(pastSession(GatheringSessionStatus.OPEN));
        GatheringSession pastCancelled = withId(buildSession(gathering, LocalDate.now().minusDays(2)));
        pastCancelled.changeStatus(GatheringSessionStatus.CANCELLED);
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)))
                .willReturn(List.of(pastOpen, pastCancelled));

        // when
        GatheringDetailResponse response = gatheringService.getGathering(gatheringId);

        // then
        assertThat(response.getSessions()).extracting(GatheringSessionResponse::getStatus)
                .containsExactly(GatheringSessionStatus.CANCELLED, GatheringSessionStatus.DONE);
    }

    // ── getSession() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("회차 상세는 종류 정보와 그 회차 1건만 반환")
    void getSession_returnsKindWithSingleSession() {
        // given
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));

        // when
        GatheringDetailResponse response = gatheringService.getSession(sessionId, AppLocale.KO);

        // then
        assertThat(response.getId()).isEqualTo(gatheringId);
        assertThat(response.getSessions()).extracting(GatheringSessionResponse::getId).containsExactly(sessionId);
    }

    @Test
    @DisplayName("없는 회차 상세는 SESSION_NOT_FOUND")
    void getSession_notFound_throwsException() {
        // given
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gatheringService.getSession(sessionId, AppLocale.KO))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_FOUND);
    }

    // ── 종류 ID 호환 (KAN-337) ────────────────────────────────────────────────

    @Test
    @DisplayName("종류 ID로 조회하면 오늘 이후 가장 이른 회차(취소 제외)를 대표 회차로 쓴다")
    void findSession_byGatheringId_picksNearestUpcomingSession() {
        // given
        GatheringSession past = buildSession(gathering, LocalDate.now().minusDays(3));
        GatheringSession cancelledSoon = buildSession(gathering, LocalDate.now().plusDays(1));
        cancelledSoon.changeStatus(GatheringSessionStatus.CANCELLED);
        GatheringSession later = buildSession(gathering, LocalDate.now().plusDays(10));
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.empty());
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)))
                .willReturn(List.of(past, later, cancelledSoon, session));

        // when
        GatheringSession result = gatheringService.findSession(gatheringId);

        // then
        assertThat(result).isSameAs(session);
    }

    @Test
    @DisplayName("다가오는 회차가 없으면 가장 최근 회차를 대표 회차로 쓴다")
    void findSession_byGatheringId_noUpcoming_picksLatestSession() {
        // given
        GatheringSession older = buildSession(gathering, LocalDate.now().minusDays(30));
        GatheringSession latest = buildSession(gathering, LocalDate.now().minusDays(2));
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.empty());
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)))
                .willReturn(List.of(older, latest));

        // when
        GatheringSession result = gatheringService.findSession(gatheringId);

        // then
        assertThat(result).isSameAs(latest);
    }

    @Test
    @DisplayName("큐레이션 id는 종류 ID가 아니라 대표 회차 ID — 마이그레이션된 종류 ID는 가장 오래된 회차 ID와 같다")
    void listCuratedGatherings_migratedKind_returnsRepresentativeSessionId() {
        // given: V5 마이그레이션 조건 — 대표 행(종류) ID == 그 행에서 복사된 지난 회차 ID
        GatheringSession oldestPast = buildSession(gathering, LocalDate.now().minusDays(200));
        ReflectionTestUtils.setField(oldestPast, "id", gatheringId);
        gathering.updateCuration(true);
        given(gatheringRepository.findByIsCuratedTrueAndDeletedAtIsNullOrderByCuratedRankAsc())
                .willReturn(List.of(gathering));
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)))
                .willReturn(List.of(oldestPast, session));

        // when
        List<CuratedGatheringResponse> result = gatheringService.listCuratedGatherings();

        // then: 카드 날짜와 클릭 시 열리는 상세가 같은 다가오는 회차여야 한다
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(sessionId).isNotEqualTo(gatheringId);
        assertThat(result.get(0).getEventDate()).isEqualTo(eventDate);
    }

    private GatheringSession pastSession(GatheringSessionStatus status) {
        GatheringSession pastSession = buildSession(gathering, LocalDate.now().minusDays(1));
        pastSession.changeStatus(status);
        return pastSession;
    }

    private GatheringSession buildSession(Gathering target, LocalDate date) {
        return GatheringSession.builder()
                .gathering(target)
                .eventDate(date)
                .maxAttendees(10)
                .build();
    }

    private static GatheringSession withId(GatheringSession target) {
        ReflectionTestUtils.setField(target, "id", UUID.randomUUID());
        return target;
    }
}
