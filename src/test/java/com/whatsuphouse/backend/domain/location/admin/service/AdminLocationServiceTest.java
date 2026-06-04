package com.whatsuphouse.backend.domain.location.admin.service;

import com.whatsuphouse.backend.domain.location.admin.dto.request.LocationCreateRequest;
import com.whatsuphouse.backend.domain.location.admin.dto.request.LocationUpdateRequest;
import com.whatsuphouse.backend.domain.location.common.dto.response.LocationDetailResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminLocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private AdminLocationService adminLocationService;

    private UUID locationId;
    private Location location;

    @BeforeEach
    void setUp() {
        locationId = UUID.randomUUID();

        location = Location.builder()
                .name("재즈바 A")
                .address("서울시 마포구 합정동 123")
                .mapUrl("https://map.kakao.com/link/map/12345678")
                .status(LocationStatus.ACTIVE)
                .maxCapacity(20)
                .memo("주차 불가")
                .build();
        ReflectionTestUtils.setField(location, "id", locationId);
    }

    // ── createLocation() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("장소 생성 성공")
    void createLocation_success() {
        // GIVEN
        LocationCreateRequest request = buildCreateRequest("홍대 카페", "서울 마포구 어울마당로 35");
        given(locationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // WHEN
        LocationDetailResponse response = adminLocationService.createLocation(request);

        // THEN
        assertThat(response.getName()).isEqualTo("홍대 카페");
        assertThat(response.getAddress()).isEqualTo("서울 마포구 어울마당로 35");
    }

    @Test
    @DisplayName("장소 생성 시 네이버·카카오 지도 URL이 저장된다")
    void createLocation_persistsProviderMapUrls() {
        // GIVEN
        LocationCreateRequest request = LocationCreateRequest.builder()
                .name("홍대 카페").address("서울 마포구 어울마당로 35")
                .mapUrl("https://naver.me/legacyMap")
                .naverMapUrl("https://naver.me/abcd1234")
                .kakaoMapUrl("https://kko.kakao.com/xyz789")
                .maxCapacity(20).status(LocationStatus.ACTIVE).build();
        given(locationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // WHEN
        LocationDetailResponse response = adminLocationService.createLocation(request);

        // THEN
        assertThat(response.getMapUrl()).isEqualTo("https://naver.me/legacyMap");
        assertThat(response.getNaverMapUrl()).isEqualTo("https://naver.me/abcd1234");
        assertThat(response.getKakaoMapUrl()).isEqualTo("https://kko.kakao.com/xyz789");
    }

    // ── updateLocation() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("장소 수정 성공")
    void updateLocation_success() {
        // GIVEN
        LocationUpdateRequest request = buildUpdateRequest("홍대 카페 (수정)", "서울 마포구 어울마당로 99");
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));

        // WHEN
        LocationDetailResponse response = adminLocationService.updateLocation(locationId, request);

        // THEN
        assertThat(response.getName()).isEqualTo("홍대 카페 (수정)");
        assertThat(response.getAddress()).isEqualTo("서울 마포구 어울마당로 99");
    }

    @Test
    @DisplayName("장소 수정 시 네이버·카카오 지도 URL이 갱신된다")
    void updateLocation_updatesProviderMapUrls() {
        // GIVEN
        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .name("홍대 카페 (수정)").address("서울 마포구 어울마당로 99")
                .mapUrl("https://naver.me/legacyUpdated")
                .naverMapUrl("https://naver.me/newNaver")
                .kakaoMapUrl("https://kko.kakao.com/newKakao")
                .maxCapacity(20).status(LocationStatus.ACTIVE).build();
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));

        // WHEN
        LocationDetailResponse response = adminLocationService.updateLocation(locationId, request);

        // THEN
        assertThat(response.getMapUrl()).isEqualTo("https://naver.me/legacyUpdated");
        assertThat(response.getNaverMapUrl()).isEqualTo("https://naver.me/newNaver");
        assertThat(response.getKakaoMapUrl()).isEqualTo("https://kko.kakao.com/newKakao");
    }

    @Test
    @DisplayName("존재하지 않는 장소 수정 시 예외 발생")
    void updateLocation_notFound_throwsException() {
        // GIVEN
        LocationUpdateRequest request = buildUpdateRequest("홍대 카페 (수정)", "서울 마포구 어울마당로 99");
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.empty());

        // WHEN & THEN
        assertThatThrownBy(() -> adminLocationService.updateLocation(locationId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_NOT_FOUND);
    }

    // ── deleteLocation() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("장소 소프트 딜리트 성공")
    void deleteLocation_success() {
        // GIVEN
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));

        // WHEN
        adminLocationService.deleteLocation(locationId);

        // THEN
        assertThat(location.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 장소 삭제 시 예외 발생")
    void deleteLocation_notFound_throwsException() {
        // GIVEN
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.empty());

        // WHEN & THEN
        assertThatThrownBy(() -> adminLocationService.deleteLocation(locationId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_NOT_FOUND);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private LocationCreateRequest buildCreateRequest(String name, String address) {
        return LocationCreateRequest.builder()
                .name(name).address(address).maxCapacity(20).build();
    }

    private LocationUpdateRequest buildUpdateRequest(String name, String address) {
        return LocationUpdateRequest.builder()
                .name(name).address(address).maxCapacity(20).status(LocationStatus.ACTIVE).build();
    }
}
