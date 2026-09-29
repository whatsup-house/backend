package com.whatsuphouse.backend.domain.gathering.service;

import com.whatsuphouse.backend.domain.gathering.client.dto.response.CuratedGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringResponse;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.enums.LocationStatus;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GatheringServiceTest {

    @Mock
    private GatheringRepository gatheringRepository;

    @Mock
    private GatheringSessionRepository gatheringSessionRepository;

    @InjectMocks
    private GatheringService gatheringService;

    private UUID sessionId;
    private Gathering gathering;
    private GatheringSession session;
    private LocalDate eventDate;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        eventDate = LocalDate.now().plusDays(7);

        gathering = Gathering.builder()
                .title("재즈 게더링")
                .basePrice(15000)
                .build();
        ReflectionTestUtils.setField(gathering, "id", UUID.randomUUID());
        session = buildSession(gathering, eventDate);
        ReflectionTestUtils.setField(session, "id", sessionId);
    }

    // ── getGatherings() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("필터 없이 전체 목록 반환 — 항목 하나가 회차 하나, id는 회차 ID")
    void getGatherings_noFilter_returnsAll() {
        given(gatheringSessionRepository.findByDeletedAtIsNull()).willReturn(List.of(session));

        List<GatheringResponse> result = gatheringService.listGatherings(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(sessionId);
        assertThat(result.get(0).getTitle()).isEqualTo("재즈 게더링");
        assertThat(result.get(0).getEventDate()).isEqualTo(eventDate);
    }

    @Test
    @DisplayName("날짜 필터만 적용하여 조회")
    void getGatherings_byDate_returnsFiltered() {
        given(gatheringSessionRepository.findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(eventDate))
                .willReturn(List.of(session));

        List<GatheringResponse> result = gatheringService.listGatherings(eventDate, null);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("상태 필터만 적용하여 조회")
    void getGatherings_byStatus_returnsFiltered() {
        given(gatheringSessionRepository.findByStatusAndDeletedAtIsNull(GatheringSessionStatus.OPEN))
                .willReturn(List.of(session));

        List<GatheringResponse> result = gatheringService.listGatherings(null, GatheringStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(GatheringStatus.OPEN);
    }

    @Test
    @DisplayName("COMPLETED 상태 필터는 회차 DONE 상태로 조회한다")
    void getGatherings_byCompletedStatus_queriesDoneSessions() {
        session.changeStatus(GatheringSessionStatus.DONE);
        given(gatheringSessionRepository.findByStatusAndDeletedAtIsNull(GatheringSessionStatus.DONE))
                .willReturn(List.of(session));

        List<GatheringResponse> result = gatheringService.listGatherings(null, GatheringStatus.COMPLETED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(GatheringStatus.COMPLETED);
    }

    @Test
    @DisplayName("날짜와 상태 복합 필터 적용하여 조회")
    void getGatherings_byDateAndStatus_returnsFiltered() {
        given(gatheringSessionRepository.findByEventDateAndStatusAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(
                eventDate, GatheringSessionStatus.OPEN))
                .willReturn(List.of(session));

        List<GatheringResponse> result = gatheringService.listGatherings(eventDate, GatheringStatus.OPEN);

        assertThat(result).hasSize(1);
    }

    // ── getGathering() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 회차 상세 조회 성공")
    void getGathering_success() {
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));

        GatheringDetailResponse response = gatheringService.getGathering(sessionId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(sessionId);
        assertThat(response.getTitle()).isEqualTo("재즈 게더링");
        assertThat(response.getStatus()).isEqualTo(GatheringStatus.OPEN);
        assertThat(response.getPrice()).isEqualTo(15000);
    }

    @Test
    @DisplayName("회차 가격 오버라이드가 있으면 종류 기본 가격 대신 쓴다")
    void getGathering_priceOverride_usesSessionPrice() {
        GatheringSession overridden = GatheringSession.builder()
                .gathering(gathering).eventDate(eventDate).maxAttendees(10).priceOverride(20000).build();
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(overridden));

        GatheringDetailResponse response = gatheringService.getGathering(sessionId);

        assertThat(response.getPrice()).isEqualTo(20000);
    }

    @Test
    @DisplayName("게더링 상세의 location에 네이버·카카오 지도 URL이 포함된다")
    void getGathering_includesLocationProviderMapUrls() {
        Location location = Location.builder()
                .name("재즈바 A")
                .address("서울시 마포구 합정동 123")
                .naverMapUrl("https://naver.me/abcd1234")
                .kakaoMapUrl("https://kko.kakao.com/xyz789")
                .status(LocationStatus.ACTIVE)
                .maxCapacity(20)
                .build();
        GatheringSession sessionWithLocation = GatheringSession.builder()
                .gathering(gathering)
                .eventDate(eventDate)
                .maxAttendees(10)
                .location(location)
                .build();
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId))
                .willReturn(Optional.of(sessionWithLocation));

        GatheringDetailResponse response = gatheringService.getGathering(sessionId);

        assertThat(response.getLocation()).isNotNull();
        assertThat(response.getLocation().getNaverMapUrl()).isEqualTo("https://naver.me/abcd1234");
        assertThat(response.getLocation().getKakaoMapUrl()).isEqualTo("https://kko.kakao.com/xyz789");
    }

    @Test
    @DisplayName("존재하지 않는 게더링 조회 시 예외 발생")
    void getGathering_notFound_throwsException() {
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.empty());
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(sessionId))).willReturn(List.of());

        assertThatThrownBy(() -> gatheringService.getGathering(sessionId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_FOUND);
    }

    // ── 종류 ID 호환 (KAN-337) ────────────────────────────────────────────────

    @Test
    @DisplayName("종류 ID로 조회하면 오늘 이후 가장 이른 회차(취소 제외)를 대표 회차로 쓴다")
    void findSession_byGatheringId_picksNearestUpcomingSession() {
        UUID gatheringId = gathering.getId();
        GatheringSession past = buildSession(gathering, LocalDate.now().minusDays(3));
        GatheringSession cancelledSoon = buildSession(gathering, LocalDate.now().plusDays(1));
        cancelledSoon.changeStatus(GatheringSessionStatus.CANCELLED);
        GatheringSession later = buildSession(gathering, LocalDate.now().plusDays(10));
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.empty());
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)))
                .willReturn(List.of(past, later, cancelledSoon, session));

        GatheringSession result = gatheringService.findSession(gatheringId);

        assertThat(result).isSameAs(session);
    }

    @Test
    @DisplayName("다가오는 회차가 없으면 가장 최근 회차를 대표 회차로 쓴다")
    void findSession_byGatheringId_noUpcoming_picksLatestSession() {
        UUID gatheringId = gathering.getId();
        GatheringSession older = buildSession(gathering, LocalDate.now().minusDays(30));
        GatheringSession latest = buildSession(gathering, LocalDate.now().minusDays(2));
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.empty());
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)))
                .willReturn(List.of(older, latest));

        GatheringSession result = gatheringService.findSession(gatheringId);

        assertThat(result).isSameAs(latest);
    }

    @Test
    @DisplayName("회차 ID로 종류를 찾으면 그 회차의 종류를 준다")
    void findGathering_bySessionId_returnsSessionGathering() {
        given(gatheringRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.empty());
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));

        Gathering result = gatheringService.findGathering(sessionId);

        assertThat(result).isSameAs(gathering);
    }

    @Test
    @DisplayName("큐레이션 id는 종류 ID가 아니라 대표 회차 ID — 마이그레이션된 종류 ID는 가장 오래된 회차 ID와 같다")
    void listCuratedGatherings_migratedKind_returnsRepresentativeSessionId() {
        // given: V5 마이그레이션 조건 — 대표 행(종류) ID == 그 행에서 복사된 지난 회차 ID
        GatheringSession oldestPast = buildSession(gathering, LocalDate.now().minusDays(200));
        ReflectionTestUtils.setField(oldestPast, "id", gathering.getId());
        gathering.updateCuration(true);
        given(gatheringRepository.findByIsCuratedTrueAndDeletedAtIsNullOrderByCuratedRankAsc())
                .willReturn(List.of(gathering));
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gathering.getId())))
                .willReturn(List.of(oldestPast, session));

        // when
        List<CuratedGatheringResponse> result = gatheringService.listCuratedGatherings();

        // then: 카드 날짜와 클릭 시 열리는 상세가 같은 다가오는 회차여야 한다
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(sessionId).isNotEqualTo(gathering.getId());
        assertThat(result.get(0).getEventDate()).isEqualTo(eventDate);
    }

    // ── 과거 게더링 상태 보정 (KAN-163) ───────────────────────────────────────

    @Test
    @DisplayName("과거 OPEN 회차 상세 조회 시 상태가 COMPLETED로 보정된다")
    void getGathering_pastOpen_returnsCompletedStatus() {
        GatheringSession pastSession = pastSession(GatheringSessionStatus.OPEN);
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(pastSession));

        GatheringDetailResponse response = gatheringService.getGathering(sessionId);

        assertThat(response.getStatus()).isEqualTo(GatheringStatus.COMPLETED);
    }

    @Test
    @DisplayName("과거 CANCELLED 회차는 상태가 그대로 유지된다")
    void getGathering_pastCancelled_keepsCancelledStatus() {
        GatheringSession cancelled = pastSession(GatheringSessionStatus.CANCELLED);
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(cancelled));

        GatheringDetailResponse response = gatheringService.getGathering(sessionId);

        assertThat(response.getStatus()).isEqualTo(GatheringStatus.CANCELLED);
    }

    @Test
    @DisplayName("status=OPEN 목록 조회 시 과거 회차는 모집중 목록에서 제외된다")
    void getGatherings_openStatus_excludesPastGathering() {
        GatheringSession pastSession = pastSession(GatheringSessionStatus.OPEN);
        given(gatheringSessionRepository.findByStatusAndDeletedAtIsNull(GatheringSessionStatus.OPEN))
                .willReturn(List.of(session, pastSession));

        List<GatheringResponse> result = gatheringService.listGatherings(null, GatheringStatus.OPEN);

        assertThat(result).hasSize(1);
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
}
