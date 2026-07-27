package com.whatsuphouse.backend.domain.carousel.admin.service;

import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideActiveRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideCreateRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideOrderRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideUpdateRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.response.AdminCarouselSlideResponse;
import com.whatsuphouse.backend.domain.carousel.entity.CarouselSlide;
import com.whatsuphouse.backend.domain.carousel.enums.SlideType;
import com.whatsuphouse.backend.domain.carousel.repository.CarouselSlideRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminCarouselServiceTest {

    @Mock
    private CarouselSlideRepository carouselSlideRepository;

    @Mock
    private GatheringRepository gatheringRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private AdminCarouselService adminCarouselService;

    private UUID slideId;
    private UUID gatheringId;
    private CarouselSlide slide;
    private Gathering gathering;

    @BeforeEach
    void setUp() {
        slideId = UUID.randomUUID();
        gatheringId = UUID.randomUUID();

        gathering = Gathering.builder()
                .title("봄 소풍 모임")
                .eventDate(LocalDate.now().plusDays(7))
                .maxAttendees(10)
                .build();
        ReflectionTestUtils.setField(gathering, "id", gatheringId);

        slide = CarouselSlide.builder()
                .type(SlideType.STORY)
                .title("봄 나들이 모임")
                .content("함께 봄꽃 구경 가요!")
                .imageUrl("temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg")
                .sortOrder(0)
                .isActive(false)
                .build();
        ReflectionTestUtils.setField(slide, "id", slideId);
    }

    // ── listSlides() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("전체 슬라이드 목록 반환")
    void listSlides_success() {
        // given
        given(carouselSlideRepository.findByDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc())
                .willReturn(List.of(slide));

        // when
        List<AdminCarouselSlideResponse> result = adminCarouselService.listSlides();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("봄 나들이 모임");
    }

    // ── createSlide() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GATHERING 타입 슬라이드 생성 성공")
    void createSlide_gatheringType_success() {
        // given
        CarouselSlideCreateRequest request = CarouselSlideCreateRequest.builder()
                .type(SlideType.GATHERING).title("5월 소풍 모임")
                .tempPath("temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg")
                .gatheringId(gatheringId).build();

        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(carouselSlideRepository.findMaxSortOrder()).willReturn(Optional.of(2));
        given(storageService.move(any(), any())).willReturn("https://cdn.example.com/slide.jpg");
        given(carouselSlideRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        AdminCarouselSlideResponse result = adminCarouselService.createSlide(request);

        // then
        assertThat(result.getType()).isEqualTo(SlideType.GATHERING);
        assertThat(result.getTitle()).isEqualTo("5월 소풍 모임");
        assertThat(result.getContent()).isNull();
    }

    @Test
    @DisplayName("STORY 타입 슬라이드 생성 성공")
    void createSlide_storyType_success() {
        // given
        CarouselSlideCreateRequest request = CarouselSlideCreateRequest.builder()
                .type(SlideType.STORY).title("봄 나들이 모임").content("함께 봄꽃 구경 가요!")
                .tempPath("temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg").build();

        given(carouselSlideRepository.findMaxSortOrder()).willReturn(Optional.empty());
        given(storageService.move(any(), any())).willReturn("https://cdn.example.com/slide.jpg");
        given(carouselSlideRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        AdminCarouselSlideResponse result = adminCarouselService.createSlide(request);

        // then
        assertThat(result.getType()).isEqualTo(SlideType.STORY);
        assertThat(result.getContent()).isEqualTo("함께 봄꽃 구경 가요!");
    }

    @Test
    @DisplayName("CALENDAR 타입 슬라이드 생성 성공")
    void createSlide_calendarType_success() {
        // given
        CarouselSlideCreateRequest request = CarouselSlideCreateRequest.builder()
                .type(SlideType.CALENDAR).title("5월 일정")
                .tempPath("https://cdn.example.com/calendar.jpg").build();

        given(carouselSlideRepository.findMaxSortOrder()).willReturn(Optional.empty());
        given(storageService.move(any(), any())).willReturn("https://cdn.example.com/slide.jpg");
        given(carouselSlideRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        AdminCarouselSlideResponse result = adminCarouselService.createSlide(request);

        // then
        assertThat(result.getType()).isEqualTo(SlideType.CALENDAR);
        assertThat(result.getTitle()).isEqualTo("5월 일정");
    }

    @Test
    @DisplayName("GATHERING 타입인데 gatheringId 없으면 GATHERING_ID_REQUIRED 예외")
    void createSlide_gatheringTypeWithoutGatheringId_throwsException() {
        // given
        CarouselSlideCreateRequest request = CarouselSlideCreateRequest.builder()
                .type(SlideType.GATHERING).title("5월 소풍 모임")
                .tempPath("temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg").build();

        // when & then
        assertThatThrownBy(() -> adminCarouselService.createSlide(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_ID_REQUIRED);
    }

    @Test
    @DisplayName("STORY 타입인데 content 없으면 SLIDE_CONTENT_REQUIRED 예외")
    void createSlide_storyTypeWithoutContent_throwsException() {
        // given
        CarouselSlideCreateRequest request = CarouselSlideCreateRequest.builder()
                .type(SlideType.STORY).title("봄 나들이 모임")
                .tempPath("temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg").build();

        // when & then
        assertThatThrownBy(() -> adminCarouselService.createSlide(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLIDE_CONTENT_REQUIRED);
    }

    @Test
    @DisplayName("GATHERING 타입에 존재하지 않는 gatheringId → GATHERING_NOT_FOUND 예외")
    void createSlide_gatheringNotFound_throwsException() {
        // given
        CarouselSlideCreateRequest request = CarouselSlideCreateRequest.builder()
                .type(SlideType.GATHERING).title("5월 소풍 모임")
                .tempPath("temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg")
                .gatheringId(gatheringId).build();

        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminCarouselService.createSlide(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_FOUND);
    }

    @Test
    @DisplayName("sortOrder 미입력 시 MAX+1 자동 배정")
    void createSlide_noSortOrder_assignsMaxPlusOne() {
        // given
        CarouselSlideCreateRequest request = CarouselSlideCreateRequest.builder()
                .type(SlideType.STORY).title("봄 나들이 모임").content("함께 봄꽃 구경 가요!")
                .tempPath("temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg").build();

        given(carouselSlideRepository.findMaxSortOrder()).willReturn(Optional.of(2));
        given(storageService.move(any(), any())).willReturn("https://cdn.example.com/slide.jpg");
        given(carouselSlideRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        AdminCarouselSlideResponse result = adminCarouselService.createSlide(request);

        // then
        assertThat(result.getSortOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("sortOrder 미입력이고 슬라이드 없을 시 0 배정")
    void createSlide_noSortOrderAndNoSlides_assignsZero() {
        // given
        CarouselSlideCreateRequest request = CarouselSlideCreateRequest.builder()
                .type(SlideType.STORY).title("봄 나들이 모임").content("함께 봄꽃 구경 가요!")
                .tempPath("temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg").build();

        given(carouselSlideRepository.findMaxSortOrder()).willReturn(Optional.empty());
        given(storageService.move(any(), any())).willReturn("https://cdn.example.com/slide.jpg");
        given(carouselSlideRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        AdminCarouselSlideResponse result = adminCarouselService.createSlide(request);

        // then
        assertThat(result.getSortOrder()).isZero();
    }

    @Test
    @DisplayName("GATHERING 타입 생성 시 content null 강제")
    void createSlide_gatheringType_contentForcedNull() {
        // given
        CarouselSlideCreateRequest request = CarouselSlideCreateRequest.builder()
                .type(SlideType.GATHERING).title("5월 소풍 모임").content("이 값은 무시되어야 함")
                .tempPath("temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg")
                .gatheringId(gatheringId).build();

        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(carouselSlideRepository.findMaxSortOrder()).willReturn(Optional.empty());
        given(storageService.move(any(), any())).willReturn("https://cdn.example.com/slide.jpg");
        given(carouselSlideRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        AdminCarouselSlideResponse result = adminCarouselService.createSlide(request);

        // then
        assertThat(result.getContent()).isNull();
    }

    // ── updateSlide() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("슬라이드 수정 성공")
    void updateSlide_success() {
        // given
        CarouselSlideUpdateRequest request = CarouselSlideUpdateRequest.builder()
                .type(SlideType.STORY).title("수정된 제목").content("수정된 내용")
                .tempPath("temp/carousel/550e8400-e29b-41d4-a716-446655440001.jpg").build();

        given(carouselSlideRepository.findByIdAndDeletedAtIsNull(slideId)).willReturn(Optional.of(slide));
        given(storageService.move(any(), any())).willReturn("https://cdn.example.com/updated.jpg");

        // when
        AdminCarouselSlideResponse result = adminCarouselService.updateSlide(slideId, request);

        // then
        assertThat(result.getTitle()).isEqualTo("수정된 제목");
        assertThat(result.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    @DisplayName("존재하지 않는 slideId 수정 시 SLIDE_NOT_FOUND 예외")
    void updateSlide_notFound_throwsException() {
        // given
        CarouselSlideUpdateRequest request = CarouselSlideUpdateRequest.builder()
                .type(SlideType.STORY).title("수정된 제목").content("수정된 내용")
                .tempPath("temp/carousel/550e8400-e29b-41d4-a716-446655440001.jpg").build();

        given(carouselSlideRepository.findByIdAndDeletedAtIsNull(slideId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminCarouselService.updateSlide(slideId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLIDE_NOT_FOUND);
    }

    // ── deleteSlide() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("슬라이드 삭제 후 deletedAt 세팅 확인")
    void deleteSlide_success_deletedAtSet() {
        // given
        given(carouselSlideRepository.findByIdAndDeletedAtIsNull(slideId)).willReturn(Optional.of(slide));

        // when
        adminCarouselService.deleteSlide(slideId);

        // then
        assertThat(slide.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 slideId 삭제 시 SLIDE_NOT_FOUND 예외")
    void deleteSlide_notFound_throwsException() {
        // given
        given(carouselSlideRepository.findByIdAndDeletedAtIsNull(slideId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminCarouselService.deleteSlide(slideId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLIDE_NOT_FOUND);
    }

    // ── toggleActive() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("isActive=true로 변경 → activate() 호출 확인")
    void toggleActive_activate_isActiveTrue() {
        // given
        CarouselSlideActiveRequest request = CarouselSlideActiveRequest.builder().isActive(true).build();

        given(carouselSlideRepository.findByIdAndDeletedAtIsNull(slideId)).willReturn(Optional.of(slide));

        // when
        adminCarouselService.toggleActive(slideId, request);

        // then
        assertThat(slide.isActive()).isTrue();
    }

    @Test
    @DisplayName("isActive=false로 변경 → deactivate() 호출 확인")
    void toggleActive_deactivate_isActiveFalse() {
        // given
        CarouselSlideActiveRequest request = CarouselSlideActiveRequest.builder().isActive(false).build();

        slide.activate();
        given(carouselSlideRepository.findByIdAndDeletedAtIsNull(slideId)).willReturn(Optional.of(slide));

        // when
        adminCarouselService.toggleActive(slideId, request);

        // then
        assertThat(slide.isActive()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 slideId 활성 전환 시 SLIDE_NOT_FOUND 예외")
    void toggleActive_notFound_throwsException() {
        // given
        CarouselSlideActiveRequest request = CarouselSlideActiveRequest.builder().isActive(true).build();

        given(carouselSlideRepository.findByIdAndDeletedAtIsNull(slideId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminCarouselService.toggleActive(slideId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLIDE_NOT_FOUND);
    }

    // ── reorderSlides() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("slideIds 순서대로 sortOrder 0,1,2 재배정 확인")
    void reorderSlides_success_sortOrderReassigned() {
        // given
        UUID slideId1 = UUID.randomUUID();
        UUID slideId2 = UUID.randomUUID();
        UUID slideId3 = UUID.randomUUID();

        CarouselSlide slide1 = CarouselSlide.builder()
                .type(SlideType.STORY).title("슬라이드1").content("내용1")
                .imageUrl("https://cdn.example.com/1.jpg").sortOrder(2).isActive(true).build();
        CarouselSlide slide2 = CarouselSlide.builder()
                .type(SlideType.STORY).title("슬라이드2").content("내용2")
                .imageUrl("https://cdn.example.com/2.jpg").sortOrder(0).isActive(true).build();
        CarouselSlide slide3 = CarouselSlide.builder()
                .type(SlideType.STORY).title("슬라이드3").content("내용3")
                .imageUrl("https://cdn.example.com/3.jpg").sortOrder(1).isActive(true).build();

        ReflectionTestUtils.setField(slide1, "id", slideId1);
        ReflectionTestUtils.setField(slide2, "id", slideId2);
        ReflectionTestUtils.setField(slide3, "id", slideId3);

        CarouselSlideOrderRequest request = CarouselSlideOrderRequest.builder()
                .slideIds(List.of(slideId1, slideId2, slideId3)).build();

        given(carouselSlideRepository.findAllByIdInAndDeletedAtIsNull(List.of(slideId1, slideId2, slideId3)))
                .willReturn(List.of(slide1, slide2, slide3));

        // when
        adminCarouselService.reorderSlides(request);

        // then
        assertThat(slide1.getSortOrder()).isZero();
        assertThat(slide2.getSortOrder()).isEqualTo(1);
        assertThat(slide3.getSortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 slideId 포함 시 SLIDE_NOT_FOUND 예외")
    void reorderSlides_notFound_throwsException() {
        // given
        UUID unknownId = UUID.randomUUID();
        CarouselSlideOrderRequest request = CarouselSlideOrderRequest.builder()
                .slideIds(List.of(unknownId)).build();

        given(carouselSlideRepository.findAllByIdInAndDeletedAtIsNull(List.of(unknownId)))
                .willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> adminCarouselService.reorderSlides(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLIDE_NOT_FOUND);
    }
}
