package com.whatsuphouse.backend.domain.review.client.controller;

import com.whatsuphouse.backend.domain.review.client.dto.request.ReviewCreateRequest;
import com.whatsuphouse.backend.domain.review.client.dto.request.ReviewUpdateRequest;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewDeleteResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewLikeResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewLocateResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewPageResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewResponse;
import com.whatsuphouse.backend.domain.review.client.service.ReviewService;
import com.whatsuphouse.backend.domain.review.enums.ReviewSort;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "리뷰", description = "리뷰 API")
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "리뷰 작성", description = "로그인한 회원이 출석 완료된 신청 건에 대해 리뷰를 작성합니다.")
    @PostMapping("/api/reviews")
    public ResponseEntity<ApiResult<ReviewResponse>> createReview(
            @Valid @RequestBody ReviewCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success("리뷰가 등록되었습니다.",
                        reviewService.createReview(request, principal.getUserId())));
    }

    @Operation(summary = "내 리뷰 수정", description = "로그인한 회원이 본인이 작성한 리뷰를 수정합니다.")
    @PatchMapping("/api/reviews/{reviewId}")
    public ResponseEntity<ApiResult<ReviewResponse>> updateReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success("리뷰가 수정되었습니다.",
                reviewService.updateReview(reviewId, request, principal.getUserId())));
    }

    @Operation(summary = "리뷰 추천/추천 취소", description = "로그인한 회원이 리뷰를 추천하거나 추천을 취소합니다.")
    @PostMapping("/api/reviews/{reviewId}/like")
    public ResponseEntity<ApiResult<ReviewLikeResponse>> toggleLike(
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(
                reviewService.toggleLike(reviewId, principal.getUserId())));
    }

    @Operation(summary = "내 리뷰 삭제", description = "로그인한 회원이 본인이 작성한 리뷰를 삭제합니다.")
    @DeleteMapping("/api/reviews/{reviewId}")
    public ResponseEntity<ApiResult<ReviewDeleteResponse>> deleteReview(
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success("리뷰가 삭제되었습니다.",
                reviewService.deleteReview(reviewId, principal.getUserId())));
    }

    @Operation(summary = "전체 리뷰 목록 조회", description = "전체 리뷰를 최신순 또는 추천순으로 조회합니다.")
    @GetMapping("/api/reviews")
    public ResponseEntity<ApiResult<ReviewPageResponse>> getReviews(
            @Parameter(description = "정렬 기준", example = "LIKES")
            @RequestParam(defaultValue = "LIKES") ReviewSort sort,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResult.success(reviewService.getReviews(sort, page, size)));
    }

    @Operation(summary = "리뷰 페이지 위치 조회", description = "전체 또는 게더링 리뷰 목록에서 특정 리뷰가 위치한 페이지 번호를 조회합니다. 프론트가 해당 페이지로 이동해 리뷰로 스크롤하는 데 사용합니다.")
    @GetMapping("/api/reviews/locate")
    public ResponseEntity<ApiResult<ReviewLocateResponse>> locateReview(
            @Parameter(description = "위치를 찾을 리뷰 ID")
            @RequestParam UUID reviewId,
            @Parameter(description = "정렬 기준", example = "LIKES")
            @RequestParam(defaultValue = "LIKES") ReviewSort sort,
            @Parameter(description = "게더링 ID (지정 시 해당 게더링 리뷰만 대상으로 페이지 계산)")
            @RequestParam(required = false) UUID gatheringId,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResult.success(
                reviewService.locateReview(reviewId, sort, gatheringId, size)));
    }

    @Operation(summary = "게더링별 리뷰 목록 조회", description = "특정 게더링에 작성된 리뷰를 최신순 또는 추천순으로 조회합니다.")
    @GetMapping("/api/gatherings/{gatheringId}/reviews")
    public ResponseEntity<ApiResult<ReviewPageResponse>> getGatheringReviews(
            @PathVariable UUID gatheringId,
            @Parameter(description = "정렬 기준", example = "LIKES")
            @RequestParam(defaultValue = "LIKES") ReviewSort sort,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResult.success(
                reviewService.getGatheringReviews(gatheringId, sort, page, size)));
    }
}
