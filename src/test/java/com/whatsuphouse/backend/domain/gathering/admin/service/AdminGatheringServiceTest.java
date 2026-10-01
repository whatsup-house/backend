package com.whatsuphouse.backend.domain.gathering.admin.service;

import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.form.admin.service.FormProvisionService;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCreateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCurationOrderRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringSessionCreateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringSessionRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringStatusRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringUpdateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.response.AdminGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringSessionResponse;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.enums.LocationStatus;
import com.whatsuphouse.backend.domain.location.repository.LocationRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminGatheringServiceTest {

    @Mock
    private GatheringRepository gatheringRepository;

    @Mock
    private GatheringSessionRepository gatheringSessionRepository;

    @Mock
    private GatheringService gatheringService;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private StorageService storageService;

    @Mock
    private FormProvisionService formProvisionService;

    @Mock
    private TicketService ticketService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AdminGatheringService adminGatheringService;

    private UUID gatheringId;
    private UUID sessionId;
    private UUID locationId;
    private Location location;
    private Gathering gathering;
    private GatheringSession session;

    @BeforeEach
    void setUp() {
        gatheringId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        locationId = UUID.randomUUID();

        location = Location.builder()
                .name("재즈바 A")
                .address("서울시 마포구 합정동 123")
                .kakaoMapUrl("https://kko.kakao.com/xyz789")
                .status(LocationStatus.ACTIVE)
                .maxCapacity(30)
                .build();
        ReflectionTestUtils.setField(location, "id", locationId);

        gathering = Gathering.builder()
                .title("재즈 게더링")
                .description("소규모 재즈 모임")
                .basePrice(15000)
                .thumbnailUrl("https://example.com/thumb.jpg")
                .build();
        ReflectionTestUtils.setField(gathering, "id", gatheringId);

        session = GatheringSession.builder()
                .gathering(gathering)
                .location(location)
                .eventDate(LocalDate.now().plusDays(7))
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .maxAttendees(10)
                .build();
        ReflectionTestUtils.setField(session, "id", sessionId);
    }

    // ── listGatherings() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("필터 없이 전체 회차 목록 반환 — 항목마다 종류 ID를 함께 준다")
    void listGatherings_noFilter_returnsAll() {
        // given
        given(gatheringSessionRepository.findByDeletedAtIsNull()).willReturn(List.of(session));
        given(applicationRepository.countBySessionIdsGroupByStatus(List.of(sessionId)))
                .willReturn(List.of());

        // when
        List<AdminGatheringResponse> result = adminGatheringService.listGatherings(null, null, null, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(sessionId);
        assertThat(result.get(0).getGatheringId()).isEqualTo(gatheringId);
        assertThat(result.get(0).getTitle()).isEqualTo("재즈 게더링");
        assertThat(result.get(0).getApplicantCount()).isZero();
    }

    @Test
    @DisplayName("status 필터로 회차 목록 반환")
    void listGatherings_withStatus_returnsFiltered() {
        // given
        given(gatheringSessionRepository.findByStatusAndDeletedAtIsNull(GatheringSessionStatus.OPEN)).willReturn(List.of(session));
        given(applicationRepository.countBySessionIdsGroupByStatus(List.of(sessionId)))
                .willReturn(List.of());

        // when
        List<AdminGatheringResponse> result = adminGatheringService.listGatherings(GatheringStatus.OPEN, null, null, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(GatheringStatus.OPEN);
    }

    @Test
    @DisplayName("조건에 맞는 회차가 없으면 빈 리스트 반환")
    void listGatherings_noMatch_returnsEmpty() {
        // given
        given(gatheringSessionRepository.findByStatusAndDeletedAtIsNull(GatheringSessionStatus.DONE)).willReturn(List.of());

        // when
        List<AdminGatheringResponse> result = adminGatheringService.listGatherings(GatheringStatus.COMPLETED, null, null, null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("eventDate 필터로 회차 목록 반환")
    void listGatherings_withEventDate_returnsFiltered() {
        // given
        LocalDate eventDate = LocalDate.now().plusDays(7);
        given(gatheringSessionRepository.findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(eventDate))
                .willReturn(List.of(session));
        given(applicationRepository.countBySessionIdsGroupByStatus(List.of(sessionId)))
                .willReturn(List.of());

        // when
        List<AdminGatheringResponse> result = adminGatheringService.listGatherings(null, eventDate, null, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventDate()).isEqualTo(eventDate);
    }

    // ── createGathering() (종류) ─────────────────────────────────────────────

    @Test
    @DisplayName("종류 생성 — 회차 없이 종류 필드·기본 가격만 저장하고 신청폼을 만든다")
    void createGathering_success() {
        // given
        GatheringCreateRequest request = GatheringCreateRequest.builder()
                .title("재즈 게더링").basePrice(20000).gatheringType(GatheringType.RANDOM_TABLE).build();
        given(gatheringRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        GatheringDetailResponse response = adminGatheringService.createGathering(request);

        // then
        assertThat(response.getTitle()).isEqualTo("재즈 게더링");
        assertThat(response.getBasePrice()).isEqualTo(20000);
        assertThat(response.getGatheringType()).isEqualTo(GatheringType.RANDOM_TABLE);
        assertThat(response.getSessions()).isEmpty();
        then(gatheringSessionRepository).should(never()).save(any());
        then(formProvisionService).should().createDefaultForm(any(Gathering.class));
    }

    @Test
    @DisplayName("thumbnailUrl가 temp 경로면 storageService.move() 호출 후 URL 저장")
    void createGathering_withThumbnailTempPath_movesFileAndSavesUrl() {
        // given
        String tempPath = "temp/gathering/550e8400.jpg";
        String movedUrl = "https://storage.example.com/gathering/550e8400.jpg";
        given(storageService.move(tempPath, "gathering")).willReturn(movedUrl);
        given(gatheringRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        GatheringDetailResponse response = adminGatheringService.createGathering(buildCreateRequest(tempPath));

        // then
        then(storageService).should().move(eq(tempPath), eq("gathering"));
        assertThat(response.getThumbnailUrl()).isEqualTo(movedUrl);
    }

    @Test
    @DisplayName("thumbnailUrl가 null·빈 문자열이면 move() 미호출, thumbnailUrl은 null")
    void createGathering_withoutThumbnail_thumbnailUrlIsNull() {
        // given
        given(gatheringRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        GatheringDetailResponse nullThumb = adminGatheringService.createGathering(buildCreateRequest(null));
        GatheringDetailResponse blankThumb = adminGatheringService.createGathering(buildCreateRequest(""));

        // then
        then(storageService).should(never()).move(any(), any());
        assertThat(nullThumb.getThumbnailUrl()).isNull();
        assertThat(blankThumb.getThumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("temp 경로가 아닌 외부 이미지 URL로 생성하면 move() 미호출, 입력값 그대로 저장")
    void createGathering_withExternalImageUrl_doesNotMoveAndStoresAsIs() {
        // given
        String externalUrl = "https://example.com/thumbnail.jpg";
        given(gatheringRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        GatheringDetailResponse response = adminGatheringService.createGathering(buildCreateRequest(externalUrl));

        // then
        then(storageService).should(never()).move(any(), any());
        assertThat(response.getThumbnailUrl()).isEqualTo(externalUrl);
    }

    // ── updateGathering() (종류) ─────────────────────────────────────────────

    @Test
    @DisplayName("종류 수정 — 제목·기본 가격이 종류에 반영된다")
    void updateGathering_success() {
        // given
        GatheringUpdateRequest request = GatheringUpdateRequest.builder()
                .title("재즈 게더링 (수정)").basePrice(18000).build();
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));

        // when
        adminGatheringService.updateGathering(gatheringId, request);

        // then
        assertThat(gathering.getTitle()).isEqualTo("재즈 게더링 (수정)");
        assertThat(gathering.getBasePrice()).isEqualTo(18000);
        then(gatheringService).should().getGathering(gatheringId);
    }

    @Test
    @DisplayName("thumbnailUrl가 temp 경로면 move() 호출 후 URL 교체")
    void updateGathering_withThumbnailTempPath_movesFileAndReplacesUrl() {
        // given
        String tempPath = "temp/gathering/new-thumb.jpg";
        String movedUrl = "https://storage.example.com/gathering/new-thumb.jpg";
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(storageService.move(tempPath, "gathering")).willReturn(movedUrl);

        // when
        adminGatheringService.updateGathering(gatheringId, buildUpdateRequest(tempPath));

        // then
        assertThat(gathering.getThumbnailUrl()).isEqualTo(movedUrl);
    }

    @Test
    @DisplayName("thumbnailUrl가 null·빈 문자열이면 move() 미호출, 기존 thumbnailUrl 유지")
    void updateGathering_withoutThumbnail_keepsPreviousThumbnailUrl() {
        // given
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));

        // when
        adminGatheringService.updateGathering(gatheringId, buildUpdateRequest(null));
        adminGatheringService.updateGathering(gatheringId, buildUpdateRequest(""));

        // then
        then(storageService).should(never()).move(any(), any());
        assertThat(gathering.getThumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
    }

    @Test
    @DisplayName("이미 저장된 공개 URL을 그대로 되돌려보내면 move() 미호출, 사진 그대로 유지하고 수정 성공")
    void updateGathering_withAlreadyStoredPublicUrl_doesNotMoveAndKeepsUrl() {
        // given: 수정 폼은 저장된 thumbnailUrl을 prefill 해서 그대로 되돌려보낸다.
        // 이 값을 다시 move() 하면 sourceKey가 없어 IMAGE_UPLOAD_FAILED로 수정이 막혔다.
        String storedUrl = "https://storage.example.com/gathering/550e8400.jpg";
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));

        // when
        adminGatheringService.updateGathering(gatheringId, buildUpdateRequest(storedUrl));

        // then
        then(storageService).should(never()).move(any(), any());
        assertThat(gathering.getThumbnailUrl()).isEqualTo(storedUrl);
    }

    @Test
    @DisplayName("상세 사진 — temp 경로만 move()하고 공개 URL은 그대로, 요청 순서대로 저장")
    void createGathering_withImageUrls_movesOnlyTempPathsKeepingOrder() {
        // given
        String tempPath = "temp/gathering/detail-1.jpg";
        String movedUrl = "https://storage.example.com/gathering/detail-1.jpg";
        String publicUrl = "https://example.com/detail-2.jpg";
        given(storageService.move(tempPath, "gathering")).willReturn(movedUrl);
        given(gatheringRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        GatheringCreateRequest request = GatheringCreateRequest.builder()
                .title("재즈 게더링").imageUrls(List.of(tempPath, publicUrl)).build();

        // when
        GatheringDetailResponse response = adminGatheringService.createGathering(request);

        // then
        assertThat(response.getImageUrls()).containsExactly(movedUrl, publicUrl);
        then(storageService).should().move(tempPath, "gathering");
        then(storageService).should(never()).move(eq(publicUrl), any());
    }

    @Test
    @DisplayName("상세 사진 수정 — 생략(null)이면 기존 유지, 빈 배열이면 모두 삭제")
    void updateGathering_imageUrls_nullKeepsEmptyClears() {
        // given
        Gathering withImages = Gathering.builder()
                .title("재즈 게더링").imageUrls(List.of("https://example.com/detail-1.jpg")).build();
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(withImages));

        // when & then
        adminGatheringService.updateGathering(gatheringId, buildUpdateRequest(null));
        assertThat(withImages.getImageUrls()).containsExactly("https://example.com/detail-1.jpg");

        adminGatheringService.updateGathering(gatheringId,
                GatheringUpdateRequest.builder().title("재즈 게더링").imageUrls(List.of()).build());
        assertThat(withImages.getImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("종류 수정은 종류 ID만 받는다 — 없는 종류면 GATHERING_NOT_FOUND")
    void updateGathering_gatheringNotFound_throwsException() {
        // given
        given(gatheringRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminGatheringService.updateGathering(sessionId, buildUpdateRequest(null)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_FOUND);
    }

    // ── deleteGathering() (종류) ─────────────────────────────────────────────

    @Test
    @DisplayName("종류 삭제 — 신청이 없으면 종류와 회차를 함께 soft delete")
    void deleteGathering_noApplications_deletesKindAndSessions() {
        // given
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)))
                .willReturn(List.of(session));
        given(applicationService.hasActiveApplications(List.of(sessionId))).willReturn(false);

        // when
        adminGatheringService.deleteGathering(gatheringId);

        // then
        assertThat(gathering.getDeletedAt()).isNotNull();
        assertThat(session.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("종류 삭제 — 신청이 있는 회차가 있으면 409 SESSION_HAS_APPLICATIONS, 아무것도 지우지 않는다")
    void deleteGathering_withApplications_throwsConflict() {
        // given
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)))
                .willReturn(List.of(session));
        given(applicationService.hasActiveApplications(List.of(sessionId))).willReturn(true);

        // when & then
        assertThatThrownBy(() -> adminGatheringService.deleteGathering(gatheringId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_HAS_APPLICATIONS);
        assertThat(gathering.getDeletedAt()).isNull();
        assertThat(session.getDeletedAt()).isNull();
    }

    // ── createSessions() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("회차 단건 생성 — 종류에 회차 하나를 붙이고 가격 오버라이드·마감을 저장한다")
    void createSessions_single_createsOneSession() {
        // given
        LocalDate date = LocalDate.now().plusDays(3);
        GatheringSessionCreateRequest request = GatheringSessionCreateRequest.builder()
                .eventDate(date).locationId(locationId).maxAttendees(8).priceOverride(20000)
                .applyDeadlineAt(date.atTime(12, 0)).build();
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));
        given(gatheringSessionRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        List<GatheringSessionResponse> result = adminGatheringService.createSessions(gatheringId, request);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventDate()).isEqualTo(date);
        assertThat(result.get(0).getPrice()).isEqualTo(20000);
        assertThat(result.get(0).getApplyDeadlineAt()).isEqualTo(date.atTime(12, 0));
        assertThat(result.get(0).getStatus()).isEqualTo(GatheringSessionStatus.OPEN);
    }

    @Test
    @DisplayName("주간 반복 생성 — 기준 날짜부터 until(포함)까지 7일 간격, 신청 마감도 같은 간격으로 민다")
    void createSessions_repeatWeekly_createsWeeklySessions() {
        // given
        LocalDate base = LocalDate.now().plusDays(1);
        LocalDateTime deadline = base.minusDays(1).atTime(18, 0);
        GatheringSessionCreateRequest request = GatheringSessionCreateRequest.builder()
                .eventDate(base).startTime(LocalTime.of(19, 0)).locationId(locationId).maxAttendees(8)
                .applyDeadlineAt(deadline)
                .repeatWeekly(new GatheringSessionCreateRequest.RepeatWeekly(base.plusWeeks(2).plusDays(3)))
                .build();
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));
        given(gatheringSessionRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        List<GatheringSessionResponse> result = adminGatheringService.createSessions(gatheringId, request);

        // then
        assertThat(result).extracting(GatheringSessionResponse::getEventDate)
                .containsExactly(base, base.plusWeeks(1), base.plusWeeks(2));
        assertThat(result).extracting(GatheringSessionResponse::getApplyDeadlineAt)
                .containsExactly(deadline, deadline.plusWeeks(1), deadline.plusWeeks(2));
        assertThat(result).extracting(GatheringSessionResponse::getStartTime).containsOnly(LocalTime.of(19, 0));
    }

    @Test
    @DisplayName("반복 종료일이 기준 날짜보다 앞서거나 1년을 넘으면 INVALID_REPEAT_RANGE")
    void createSessions_repeatOutOfRange_throwsException() {
        // given
        LocalDate base = LocalDate.now().plusDays(1);
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));

        // when & then
        for (LocalDate until : List.of(base.minusDays(1), base.plusYears(1).plusDays(1))) {
            GatheringSessionCreateRequest request = GatheringSessionCreateRequest.builder()
                    .eventDate(base).locationId(locationId).maxAttendees(8)
                    .repeatWeekly(new GatheringSessionCreateRequest.RepeatWeekly(until)).build();
            assertThatThrownBy(() -> adminGatheringService.createSessions(gatheringId, request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REPEAT_RANGE);
        }
        then(gatheringSessionRepository).should(never()).saveAll(anyList());
    }

    @Test
    @DisplayName("과거 날짜로 회차를 만들면 INVALID_GATHERING_DATE (KAN-221)")
    void createSessions_pastDate_throwsException() {
        // given
        GatheringSessionCreateRequest request = GatheringSessionCreateRequest.builder()
                .eventDate(LocalDate.now().minusDays(1)).locationId(locationId).maxAttendees(10).build();
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));

        // when & then
        assertThatThrownBy(() -> adminGatheringService.createSessions(gatheringId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_GATHERING_DATE);
    }

    @Test
    @DisplayName("종료 시간이 시작 시간보다 이르면 다음날 새벽 종료로 보고 생성 허용 (KAN-293)")
    void createSessions_overnightTime_allowsCreation() {
        // given
        GatheringSessionCreateRequest request = GatheringSessionCreateRequest.builder()
                .eventDate(LocalDate.now().plusDays(7)).locationId(locationId).maxAttendees(10)
                .startTime(LocalTime.of(20, 0)).endTime(LocalTime.of(2, 0)).build();
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));
        given(gatheringSessionRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        List<GatheringSessionResponse> result = adminGatheringService.createSessions(gatheringId, request);

        // then
        assertThat(result.get(0).getStartTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(result.get(0).getEndTime()).isEqualTo(LocalTime.of(2, 0));
    }

    @Test
    @DisplayName("존재하지 않는 장소로 회차를 만들면 LOCATION_NOT_FOUND")
    void createSessions_locationNotFound_throwsException() {
        // given
        GatheringSessionCreateRequest request = GatheringSessionCreateRequest.builder()
                .eventDate(LocalDate.now().plusDays(7)).locationId(locationId).maxAttendees(10).build();
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminGatheringService.createSessions(gatheringId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_NOT_FOUND);
    }

    // ── updateSession() / deleteSession() ────────────────────────────────────

    @Test
    @DisplayName("회차 수정 — 날짜·정원·가격 오버라이드·마감이 회차에만 반영된다")
    void updateSession_success() {
        // given
        LocalDate newDate = LocalDate.now().plusDays(10);
        GatheringSessionRequest request = GatheringSessionRequest.builder()
                .eventDate(newDate).locationId(locationId).maxAttendees(12).priceOverride(0)
                .applyDeadlineAt(newDate.atStartOfDay()).build();
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
        given(locationRepository.findByIdAndDeletedAtIsNull(locationId)).willReturn(Optional.of(location));
        given(gatheringService.toSessionResponses(List.of(session)))
                .willAnswer(inv -> List.of(GatheringSessionResponse.from(session, 0)));

        // when
        GatheringSessionResponse response = adminGatheringService.updateSession(sessionId, request);

        // then
        assertThat(response.getEventDate()).isEqualTo(newDate);
        assertThat(session.getMaxAttendees()).isEqualTo(12);
        assertThat(session.getEffectivePrice()).isZero();
        assertThat(session.getApplyDeadlineAt()).isEqualTo(newDate.atStartOfDay());
        assertThat(gathering.getBasePrice()).isEqualTo(15000);
    }

    @Test
    @DisplayName("회차 삭제 — 신청이 있으면 409 SESSION_HAS_APPLICATIONS")
    void deleteSession_withApplications_throwsConflict() {
        // given
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
        given(applicationService.hasActiveApplications(List.of(sessionId))).willReturn(true);

        // when & then
        assertThatThrownBy(() -> adminGatheringService.deleteSession(sessionId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_HAS_APPLICATIONS);
        assertThat(session.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("회차 삭제 — 신청이 없으면 soft delete")
    void deleteSession_noApplications_softDeletes() {
        // given
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
        given(applicationService.hasActiveApplications(List.of(sessionId))).willReturn(false);

        // when
        adminGatheringService.deleteSession(sessionId);

        // then
        assertThat(session.getDeletedAt()).isNotNull();
    }

    // ── changeStatus() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("회차 상태 CLOSED로 변경 성공")
    void changeStatus_toClosed_success() {
        // given
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));

        // when
        adminGatheringService.changeStatus(sessionId, buildStatusRequest(GatheringStatus.CLOSED));

        // then
        assertThat(session.getStatus()).isEqualTo(GatheringSessionStatus.CLOSED);
    }

    @Test
    @DisplayName("회차 상태 COMPLETED로 변경하면 DONE")
    void changeStatus_toCompleted_success() {
        // given
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));

        // when
        adminGatheringService.changeStatus(sessionId, buildStatusRequest(GatheringStatus.COMPLETED));

        // then
        assertThat(session.getStatus()).isEqualTo(GatheringSessionStatus.DONE);
    }

    @Test
    @DisplayName("회차 상태 CANCELLED로 변경 성공")
    void changeStatus_toCancelled_success() {
        // given
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));

        // when
        adminGatheringService.changeStatus(sessionId, buildStatusRequest(GatheringStatus.CANCELLED));

        // then
        assertThat(session.getStatus()).isEqualTo(GatheringSessionStatus.CANCELLED);
    }

    @Test
    @DisplayName("우연한 식탁 회차 취소 시 회원 신청자에게 이용권을 환불한다 (KAN-261)")
    void changeStatus_cancelRandomTable_refundsTicketsToMembers() {
        // given
        Gathering randomTable = Gathering.builder()
                .title("우연한 식탁")
                .gatheringType(GatheringType.RANDOM_TABLE)
                .build();
        ReflectionTestUtils.setField(randomTable, "id", gatheringId);
        GatheringSession randomTableSession = GatheringSession.builder()
                .gathering(randomTable)
                .eventDate(LocalDate.now().plusDays(7))
                .maxAttendees(8)
                .build();
        ReflectionTestUtils.setField(randomTableSession, "id", sessionId);

        User member = buildMember("member@example.com", "member1");
        Application memberApp = buildApplication(randomTableSession, member);
        Application guestApp = buildApplication(randomTableSession, null); // 비회원: 환불 대상 아님

        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(randomTableSession));
        given(applicationRepository.findBySessionIdAndStatusInWithUser(eq(sessionId), any()))
                .willReturn(List.of(memberApp, guestApp));

        // when
        adminGatheringService.changeStatus(sessionId, buildStatusRequest(GatheringStatus.CANCELLED));

        // then
        then(ticketService).should().refundOneTicket(memberApp);
        then(ticketService).should().refundOneTicket(guestApp);
    }

    @Test
    @DisplayName("존재하지 않는 회차 상태 변경 시 SESSION_NOT_FOUND")
    void changeStatus_notFound_throwsException() {
        // given
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminGatheringService.changeStatus(sessionId, buildStatusRequest(GatheringStatus.CLOSED)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_FOUND);
    }

    // ── reorderCurated() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("큐레이션 순서 변경은 큐레이션 목록의 대표 회차 ID를 종류로 해석해 순위를 매긴다 (KAN-337)")
    void reorderCurated_representativeSessionIds_updatesKindRank() {
        // given
        Gathering other = Gathering.builder().title("재즈 게더링 2").build();
        UUID otherSessionId = UUID.randomUUID();
        given(gatheringService.findGathering(otherSessionId)).willReturn(other);
        given(gatheringService.findGathering(sessionId)).willReturn(gathering);
        GatheringCurationOrderRequest request = new GatheringCurationOrderRequest();
        ReflectionTestUtils.setField(request, "gatheringIds", List.of(otherSessionId, sessionId));

        // when
        adminGatheringService.reorderCurated(request);

        // then
        assertThat(other.getCuratedRank()).isZero();
        assertThat(gathering.getCuratedRank()).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildMember(String email, String nickname) {
        User member = User.builder()
                .email(email)
                .password("encoded")
                .name("김회원")
                .gender(Gender.FEMALE)
                .age(28)
                .nickname(nickname)
                .phone("01099998888")
                .build();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        return member;
    }

    private Application buildApplication(GatheringSession targetSession, User user) {
        Application app = Application.builder()
                .bookingNumber("WH260618-RT" + UUID.randomUUID().toString().substring(0, 4))
                .session(targetSession)
                .user(user)
                .name(user != null ? user.getName() : "비회원")
                .phone("01000000000")
                .build();
        ReflectionTestUtils.setField(app, "id", UUID.randomUUID());
        return app;
    }

    private GatheringCreateRequest buildCreateRequest(String thumbnailUrl) {
        return GatheringCreateRequest.builder().title("재즈 게더링").thumbnailUrl(thumbnailUrl).build();
    }

    private GatheringUpdateRequest buildUpdateRequest(String thumbnailUrl) {
        return GatheringUpdateRequest.builder().title("재즈 게더링 (수정)").thumbnailUrl(thumbnailUrl).build();
    }

    private GatheringStatusRequest buildStatusRequest(GatheringStatus status) {
        return GatheringStatusRequest.builder().status(status).build();
    }
}
