package com.whatsuphouse.backend.domain.location.service;

import com.whatsuphouse.backend.domain.location.client.service.LocationService;
import com.whatsuphouse.backend.domain.location.common.dto.response.LocationDetailResponse;
import com.whatsuphouse.backend.domain.location.common.dto.response.LocationResponse;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.enums.LocationStatus;
import com.whatsuphouse.backend.domain.location.repository.LocationRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private LocationService locationService;

    private UUID locationId;
    private Location location;

    @BeforeEach
    void setUp() {
        locationId = UUID.randomUUID();

        location = Location.builder()
                .name("재즈바 A")
                .address("서울시 마포구 합정동 123")
                .naverMapUrl("https://naver.me/abcd1234")
                .kakaoMapUrl("https://kko.kakao.com/xyz789")
                .status(LocationStatus.ACTIVE)
                .maxCapacity(20)
                .build();
    }

    // ── getLocations() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("전체 장소 목록 반환")
    void getLocations_returnsAll() {
        given(locationRepository.findByDeletedAtIsNull()).willReturn(List.of(location));

        List<LocationResponse> result = locationService.getLocations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("재즈바 A");
    }

    @Test
    @DisplayName("장소 목록 응답에 네이버·카카오 지도 URL이 포함된다")
    void getLocations_includesProviderMapUrls() {
        given(locationRepository.findByDeletedAtIsNull()).willReturn(List.of(location));

        List<LocationResponse> result = locationService.getLocations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNaverMapUrl()).isEqualTo("https://naver.me/abcd1234");
        assertThat(result.get(0).getKakaoMapUrl()).isEqualTo("https://kko.kakao.com/xyz789");
    }

    @Test
    @DisplayName("장소가 없으면 빈 리스트 반환")
    void getLocations_empty_returnsEmptyList() {
        given(locationRepository.findByDeletedAtIsNull()).willReturn(List.of());

        List<LocationResponse> result = locationService.getLocations();

        assertThat(result).isEmpty();
    }

    // ── getLocation() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 장소 상세 조회 성공")
    void getLocation_success() {
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));

        LocationDetailResponse response = locationService.getLocation(locationId);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("재즈바 A");
        assertThat(response.getAddress()).isEqualTo("서울시 마포구 합정동 123");
    }

    @Test
    @DisplayName("장소 상세 응답에 네이버·카카오 지도 URL이 포함된다")
    void getLocation_includesProviderMapUrls() {
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));

        LocationDetailResponse response = locationService.getLocation(locationId);

        assertThat(response.getNaverMapUrl()).isEqualTo("https://naver.me/abcd1234");
        assertThat(response.getKakaoMapUrl()).isEqualTo("https://kko.kakao.com/xyz789");
    }

    @Test
    @DisplayName("존재하지 않는 장소 조회 시 예외 발생")
    void getLocation_notFound_throwsException() {
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.getLocation(locationId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_NOT_FOUND);
    }
}
