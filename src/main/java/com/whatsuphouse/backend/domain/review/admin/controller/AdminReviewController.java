package com.whatsuphouse.backend.domain.review.admin.controller;

import com.whatsuphouse.backend.domain.review.admin.dto.request.ReviewHomeFeaturedRequest;
import com.whatsuphouse.backend.domain.review.admin.dto.request.ReviewHomeOrderRequest;
import com.whatsuphouse.backend.domain.review.admin.dto.response.AdminReviewPageResponse;
import com.whatsuphouse.backend.domain.review.admin.dto.response.AdminReviewResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewDeleteResponse;
import com.whatsuphouse.backend.domain.review.admin.service.AdminReviewService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "리뷰 관리 (관리자)", description = "관리자 리뷰 홈 노출 관리 API")
@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    @Operation(summary = "리뷰 목록 조회", description = "관리자 권한이 필요합니다.")
    @GetMapping
    public ResponseEntity<ApiResult<AdminReviewPageResponse>> listReviews(
            @Parameter(description = "홈 노출 여부 필터")
            @RequestParam(required = false) Boolean homeFeatured,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResult.success(adminReviewService.listReviews(homeFeatured, page, size)));
    }

    @Operation(summary = "리뷰 홈 노출 설정 변경", description = "관리자 권한이 필요합니다.")
    @PatchMapping("/{reviewId}/home-featured")
    public ResponseEntity<ApiResult<AdminReviewResponse>> updateHomeFeatured(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewHomeFeaturedRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success("리뷰 홈 노출 설정이 변경되었습니다.",
                adminReviewService.updateHomeFeatured(reviewId, request)));
    }

    @Operation(summary = "홈 노출 리뷰 순서 변경", description = "관리자 권한이 필요합니다.")
    @PutMapping("/home-order")
    public ResponseEntity<ApiResult<Void>> reorderHomeReviews(
            @Valid @RequestBody ReviewHomeOrderRequest request
    ) {
        adminReviewService.reorderHomeReviews(request);
        return ResponseEntity.ok(ApiResult.success("홈 노출 리뷰 순서가 변경되었습니다.", null));
    }

    @Operation(summary = "관리자 리뷰 삭제", description = "관리자 권한이 필요합니다.")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResult<ReviewDeleteResponse>> deleteReview(
            @PathVariable UUID reviewId
    ) {
        return ResponseEntity.ok(ApiResult.success("리뷰가 삭제되었습니다.",
                adminReviewService.deleteReview(reviewId)));
    }
}
