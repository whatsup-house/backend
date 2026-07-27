package com.whatsuphouse.backend.domain.carousel.admin.controller;

import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideActiveRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideCreateRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideOrderRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideUpdateRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.response.AdminCarouselSlideResponse;
import com.whatsuphouse.backend.domain.carousel.admin.service.AdminCarouselService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "캐러셀 관리 (관리자)", description = "관리자 캐러셀 슬라이드 CRUD API")
@RestController
@RequestMapping("/api/admin/carousel")
@RequiredArgsConstructor
public class AdminCarouselController {

    private final AdminCarouselService adminCarouselService;

    @Operation(summary = "슬라이드 목록 조회", description = "관리자 권한이 필요합니다.")
    @GetMapping
    public ResponseEntity<ApiResult<List<AdminCarouselSlideResponse>>> listSlides() {
        return ResponseEntity.ok(ApiResult.success(adminCarouselService.listSlides()));
    }

    @Operation(summary = "슬라이드 등록", description = "관리자 권한이 필요합니다.")
    @PostMapping
    public ResponseEntity<ApiResult<AdminCarouselSlideResponse>> createSlide(
            @Valid @RequestBody CarouselSlideCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success("슬라이드가 등록되었습니다.", adminCarouselService.createSlide(request)));
    }

    @Operation(summary = "슬라이드 수정", description = "관리자 권한이 필요합니다.")
    @PutMapping("/{slideId}")
    public ResponseEntity<ApiResult<AdminCarouselSlideResponse>> updateSlide(
            @PathVariable UUID slideId,
            @Valid @RequestBody CarouselSlideUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success("슬라이드가 수정되었습니다.", adminCarouselService.updateSlide(slideId, request)));
    }

    @Operation(summary = "슬라이드 삭제", description = "관리자 권한이 필요합니다.")
    @DeleteMapping("/{slideId}")
    public ResponseEntity<ApiResult<Void>> deleteSlide(
            @PathVariable UUID slideId
    ) {
        adminCarouselService.deleteSlide(slideId);
        return ResponseEntity.ok(ApiResult.success("슬라이드가 삭제되었습니다.", null));
    }

    @Operation(summary = "슬라이드 활성 상태 변경", description = "관리자 권한이 필요합니다.")
    @PatchMapping("/{slideId}")
    public ResponseEntity<ApiResult<Void>> toggleActive(
            @PathVariable UUID slideId,
            @Valid @RequestBody CarouselSlideActiveRequest request
    ) {
        adminCarouselService.toggleActive(slideId, request);
        return ResponseEntity.ok(ApiResult.success("슬라이드 활성 상태가 변경되었습니다.", null));
    }

    @Operation(summary = "슬라이드 순서 변경", description = "관리자 권한이 필요합니다.")
    @PutMapping("/order")
    public ResponseEntity<ApiResult<Void>> reorderSlides(
            @Valid @RequestBody CarouselSlideOrderRequest request
    ) {
        adminCarouselService.reorderSlides(request);
        return ResponseEntity.ok(ApiResult.success("슬라이드 순서가 변경되었습니다.", null));
    }
}
