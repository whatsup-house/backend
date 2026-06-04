package com.whatsuphouse.backend.domain.gathering.service;

import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringResponse;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
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

    @InjectMocks
    private GatheringService gatheringService;

    private UUID gatheringId;
    private Gathering gathering;
    private LocalDate eventDate;

    @BeforeEach
    void setUp() {
        gatheringId = UUID.randomUUID();
        eventDate = LocalDate.now().plusDays(7);

        gathering = Gathering.builder()
                .title("재즈 게더링")
                .eventDate(eventDate)
                .maxAttendees(10)
                .build();
    }

    // ── getGatherings() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("필터 없이 전체 목록 반환")
    void getGatherings_noFilter_returnsAll() {
        given(gatheringRepository.findByDeletedAtIsNull()).willReturn(List.of(gathering));

        List<GatheringResponse> result = gatheringService.listGatherings(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("재즈 게더링");
    }

    @Test
    @DisplayName("날짜 필터만 적용하여 조회")
    void getGatherings_byDate_returnsFiltered() {
        given(gatheringRepository.findByEventDateAndDeletedAtIsNull(eventDate)).willReturn(List.of(gathering));

        List<GatheringResponse> result = gatheringService.listGatherings(eventDate, null);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("상태 필터만 적용하여 조회")
    void getGatherings_byStatus_returnsFiltered() {
        given(gatheringRepository.findByStatusAndDeletedAtIsNull(GatheringStatus.OPEN)).willReturn(List.of(gathering));

        List<GatheringResponse> result = gatheringService.listGatherings(null, GatheringStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(GatheringStatus.OPEN);
    }

    @Test
    @DisplayName("날짜와 상태 복합 필터 적용하여 조회")
    void getGatherings_byDateAndStatus_returnsFiltered() {
        given(gatheringRepository.findByEventDateAndStatusAndDeletedAtIsNull(eventDate, GatheringStatus.OPEN))
                .willReturn(List.of(gathering));

        List<GatheringResponse> result = gatheringService.listGatherings(eventDate, GatheringStatus.OPEN);

        assertThat(result).hasSize(1);
    }

    // ── getGathering() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 게더링 상세 조회 성공")
    void getGathering_success() {
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));

        GatheringDetailResponse response = gatheringService.getGathering(gatheringId);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("재즈 게더링");
        assertThat(response.getStatus()).isEqualTo(GatheringStatus.OPEN);
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
        Gathering gatheringWithLocation = Gathering.builder()
                .title("재즈 게더링")
                .eventDate(eventDate)
                .maxAttendees(10)
                .location(location)
                .build();
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(gatheringWithLocation));

        GatheringDetailResponse response = gatheringService.getGathering(gatheringId);

        assertThat(response.getLocation()).isNotNull();
        assertThat(response.getLocation().getNaverMapUrl()).isEqualTo("https://naver.me/abcd1234");
        assertThat(response.getLocation().getKakaoMapUrl()).isEqualTo("https://kko.kakao.com/xyz789");
    }

    @Test
    @DisplayName("존재하지 않는 게더링 조회 시 예외 발생")
    void getGathering_notFound_throwsException() {
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> gatheringService.getGathering(gatheringId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_FOUND);
    }
}
