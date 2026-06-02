package com.whatsuphouse.backend.domain.gathering.admin.service;

import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCreateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringStatusRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringUpdateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.response.AdminGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.enums.LocationStatus;
import com.whatsuphouse.backend.domain.form.admin.service.FormProvisionService;
import com.whatsuphouse.backend.domain.location.repository.LocationRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.global.storage.service.StorageService;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminGatheringServiceTest {

    @Mock
    private GatheringRepository gatheringRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private FormProvisionService formProvisionService;

    @InjectMocks
    private AdminGatheringService adminGatheringService;

    private UUID gatheringId;
    private UUID locationId;
    private Location location;
    private Gathering gathering;

    @BeforeEach
    void setUp() {
        gatheringId = UUID.randomUUID();
        locationId = UUID.randomUUID();

        location = Location.builder()
                .name("재즈바 A")
                .address("서울시 마포구 합정동 123")
                .mapUrl("https://map.kakao.com/link/map/12345678")
                .status(LocationStatus.ACTIVE)
                .maxCapacity(30)
                .build();
        ReflectionTestUtils.setField(location, "id", locationId);

        gathering = Gathering.builder()
                .title("재즈 게더링")
                .description("소규모 재즈 모임")
                .location(location)
                .eventDate(LocalDate.now().plusDays(7))
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .price(15000)
                .maxAttendees(10)
                .thumbnailUrl("https://example.com/thumb.jpg")
                .build();
        ReflectionTestUtils.setField(gathering, "id", gatheringId);
    }

    // ── listGatherings() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("필터 없이 전체 게더링 목록 반환")
    void listGatherings_noFilter_returnsAll() {
        // given
        given(gatheringRepository.findByDeletedAtIsNull()).willReturn(List.of(gathering));
        given(applicationRepository.countByGatheringIdsGroupByStatus(List.of(gatheringId)))
                .willReturn(List.of());

        // when
        List<AdminGatheringResponse> result = adminGatheringService.listGatherings(null, null, null, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("재즈 게더링");
        assertThat(result.get(0).getApplicantCount()).isZero();
    }

    @Test
    @DisplayName("status 필터로 게더링 목록 반환")
    void listGatherings_withStatus_returnsFiltered() {
        // given
        given(gatheringRepository.findByStatusAndDeletedAtIsNull(GatheringStatus.OPEN)).willReturn(List.of(gathering));
        given(applicationRepository.countByGatheringIdsGroupByStatus(List.of(gatheringId)))
                .willReturn(List.of());

        // when
        List<AdminGatheringResponse> result = adminGatheringService.listGatherings(GatheringStatus.OPEN, null, null, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(GatheringStatus.OPEN);
    }

    @Test
    @DisplayName("조건에 맞는 게더링이 없으면 빈 리스트 반환")
    void listGatherings_noMatch_returnsEmpty() {
        // given
        given(gatheringRepository.findByStatusAndDeletedAtIsNull(GatheringStatus.COMPLETED)).willReturn(List.of());

        // when
        List<AdminGatheringResponse> result = adminGatheringService.listGatherings(GatheringStatus.COMPLETED, null, null, null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("eventDate 필터로 게더링 목록 반환")
    void listGatherings_withEventDate_returnsFiltered() {
        // given
        LocalDate eventDate = LocalDate.now().plusDays(7);
        given(gatheringRepository.findByEventDateAndDeletedAtIsNull(eventDate)).willReturn(List.of(gathering));
        given(applicationRepository.countByGatheringIdsGroupByStatus(List.of(gatheringId)))
                .willReturn(List.of());

        // when
        List<AdminGatheringResponse> result = adminGatheringService.listGatherings(null, eventDate, null, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventDate()).isEqualTo(eventDate);
    }

    // ── createGathering() ────────────────────────────────────────────────────

    @Test
    @DisplayName("thumbnailUrl가 있으면 storageService.move() 호출 후 URL 저장")
    void createGathering_withThumbnailTempPath_movesFileAndSavesUrl() {
        // given
        String tempPath = "temp/gathering/550e8400.jpg";
        String movedUrl = "https://storage.example.com/gathering/550e8400.jpg";
        GatheringCreateRequest request = buildCreateRequest("재즈 게더링", tempPath);
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));
        given(storageService.move(tempPath, "gathering")).willReturn(movedUrl);
        given(gatheringRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        GatheringDetailResponse response = adminGatheringService.createGathering(request);

        // then
        then(storageService).should().move(eq(tempPath), eq("gathering"));
        assertThat(response.getThumbnailUrl()).isEqualTo(movedUrl);
    }

    @Test
    @DisplayName("thumbnailUrl가 null이면 storageService.move() 미호출, thumbnailUrl은 null")
    void createGathering_withoutThumbnailTempPath_thumbnailUrlIsNull() {
        // given
        GatheringCreateRequest request = buildCreateRequest("재즈 게더링", null);
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));
        given(gatheringRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        GatheringDetailResponse response = adminGatheringService.createGathering(request);

        // then
        then(storageService).should(never()).move(any(), any());
        assertThat(response.getThumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("thumbnailUrl가 빈 문자열이면 storageService.move() 미호출, thumbnailUrl은 null")
    void createGathering_withBlankThumbnailTempPath_thumbnailUrlIsNull() {
        // given
        GatheringCreateRequest request = buildCreateRequest("재즈 게더링", "");
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));
        given(gatheringRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        GatheringDetailResponse response = adminGatheringService.createGathering(request);

        // then
        then(storageService).should(never()).move(any(), any());
        assertThat(response.getThumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("thumbnailUrl가 빈 문자열이면 storageService.move() 미호출, 기존 thumbnailUrl 유지")
    void updateGathering_withBlankThumbnailTempPath_keepsPreviousThumbnailUrl() {
        // given
        String existingUrl = "https://example.com/thumb.jpg";
        GatheringUpdateRequest request = buildUpdateRequest("재즈 게더링 (수정)", "");
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));

        // when
        GatheringDetailResponse response = adminGatheringService.updateGathering(gatheringId, request);

        // then
        then(storageService).should(never()).move(any(), any());
        assertThat(response.getThumbnailUrl()).isEqualTo(existingUrl);
    }

    @Test
    @DisplayName("게더링 생성 성공 - 기본 필드 검증")
    void createGathering_success() {
        // given
        GatheringCreateRequest request = buildCreateRequest("재즈 게더링", null);
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));
        given(gatheringRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        GatheringDetailResponse response = adminGatheringService.createGathering(request);

        // then
        assertThat(response.getTitle()).isEqualTo("재즈 게더링");
        assertThat(response.getStatus()).isEqualTo(GatheringStatus.OPEN);
        assertThat(response.getLocation().getName()).isEqualTo("재즈바 A");
    }

    @Test
    @DisplayName("존재하지 않는 장소로 게더링 생성 시 예외 발생")
    void createGathering_locationNotFound_throwsException() {
        // given
        GatheringCreateRequest request = buildCreateRequest("재즈 게더링", null);
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminGatheringService.createGathering(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_NOT_FOUND);
    }

    // ── updateGathering() ────────────────────────────────────────────────────

    @Test
    @DisplayName("thumbnailUrl가 있으면 storageService.move() 호출 후 URL 교체")
    void updateGathering_withThumbnailTempPath_movesFileAndReplacesUrl() {
        // given
        String tempPath = "temp/gathering/new-thumb.jpg";
        String movedUrl = "https://storage.example.com/gathering/new-thumb.jpg";
        GatheringUpdateRequest request = buildUpdateRequest("재즈 게더링 (수정)", tempPath);
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));
        given(storageService.move(tempPath, "gathering")).willReturn(movedUrl);

        // when
        GatheringDetailResponse response = adminGatheringService.updateGathering(gatheringId, request);

        // then
        then(storageService).should().move(eq(tempPath), eq("gathering"));
        assertThat(response.getThumbnailUrl()).isEqualTo(movedUrl);
    }

    @Test
    @DisplayName("thumbnailUrl가 null이면 storageService.move() 미호출, 기존 thumbnailUrl 유지")
    void updateGathering_withoutThumbnailTempPath_keepsPreviousThumbnailUrl() {
        // given
        String existingUrl = "https://example.com/thumb.jpg";
        GatheringUpdateRequest request = buildUpdateRequest("재즈 게더링 (수정)", null);
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));

        // when
        GatheringDetailResponse response = adminGatheringService.updateGathering(gatheringId, request);

        // then
        then(storageService).should(never()).move(any(), any());
        assertThat(response.getThumbnailUrl()).isEqualTo(existingUrl);
    }

    @Test
    @DisplayName("게더링 수정 성공 - 기본 필드 검증")
    void updateGathering_success() {
        // given
        GatheringUpdateRequest request = buildUpdateRequest("재즈 게더링 (수정)", null);
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));

        // when
        GatheringDetailResponse response = adminGatheringService.updateGathering(gatheringId, request);

        // then
        assertThat(response.getTitle()).isEqualTo("재즈 게더링 (수정)");
    }

    @Test
    @DisplayName("존재하지 않는 게더링 수정 시 예외 발생")
    void updateGathering_gatheringNotFound_throwsException() {
        // given
        GatheringUpdateRequest request = buildUpdateRequest("재즈 게더링 (수정)", null);
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminGatheringService.updateGathering(gatheringId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 장소로 게더링 수정 시 예외 발생")
    void updateGathering_locationNotFound_throwsException() {
        // given
        GatheringUpdateRequest request = buildUpdateRequest("재즈 게더링 (수정)", null);
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminGatheringService.updateGathering(gatheringId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_NOT_FOUND);
    }

    // ── changeStatus() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("게더링 상태 CLOSED로 변경 성공")
    void changeStatus_toClosed_success() {
        // given
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        GatheringStatusRequest request = buildStatusRequest(GatheringStatus.CLOSED);

        // when
        adminGatheringService.changeStatus(gatheringId, request);

        // then
        assertThat(gathering.getStatus()).isEqualTo(GatheringStatus.CLOSED);
    }

    @Test
    @DisplayName("게더링 상태 COMPLETED로 변경 성공")
    void changeStatus_toCompleted_success() {
        // given
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        GatheringStatusRequest request = buildStatusRequest(GatheringStatus.COMPLETED);

        // when
        adminGatheringService.changeStatus(gatheringId, request);

        // then
        assertThat(gathering.getStatus()).isEqualTo(GatheringStatus.COMPLETED);
    }

    @Test
    @DisplayName("게더링 상태 CANCELLED로 변경 성공")
    void changeStatus_toCancelled_success() {
        // given
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        GatheringStatusRequest request = buildStatusRequest(GatheringStatus.CANCELLED);

        // when
        adminGatheringService.changeStatus(gatheringId, request);

        // then
        assertThat(gathering.getStatus()).isEqualTo(GatheringStatus.CANCELLED);
    }

    @Test
    @DisplayName("존재하지 않는 게더링 상태 변경 시 예외 발생")
    void changeStatus_notFound_throwsException() {
        // given
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.empty());
        GatheringStatusRequest request = buildStatusRequest(GatheringStatus.CLOSED);

        // when & then
        assertThatThrownBy(() -> adminGatheringService.changeStatus(gatheringId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_FOUND);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private GatheringCreateRequest buildCreateRequest(String title, String thumbnailUrl) {
        return GatheringCreateRequest.builder()
                .title(title).locationId(locationId)
                .eventDate(LocalDate.now().plusDays(7)).maxAttendees(10)
                .thumbnailUrl(thumbnailUrl).build();
    }

    private GatheringUpdateRequest buildUpdateRequest(String title, String thumbnailUrl) {
        return GatheringUpdateRequest.builder()
                .title(title).locationId(locationId)
                .eventDate(LocalDate.now().plusDays(7)).maxAttendees(10)
                .thumbnailUrl(thumbnailUrl).build();
    }

    private GatheringStatusRequest buildStatusRequest(GatheringStatus status) {
        return GatheringStatusRequest.builder().status(status).build();
    }
}
