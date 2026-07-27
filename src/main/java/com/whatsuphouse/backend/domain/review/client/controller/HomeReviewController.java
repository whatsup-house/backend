package com.whatsuphouse.backend.domain.review.client.controller;

import com.whatsuphouse.backend.domain.review.client.dto.response.HomeReviewResponse;
import com.whatsuphouse.backend.domain.review.client.service.ReviewService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "홈 화면 (리뷰)", description = "홈 화면 리뷰 목록 조회 API")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "홈 노출 리뷰 목록 조회", description = "홈 화면에 노출할 리뷰 목록을 노출 순서대로 반환합니다. 인증 불필요.")
    @GetMapping("/reviews")
    public ResponseEntity<ApiResult<List<HomeReviewResponse>>> listHomeReviews() {
        return ResponseEntity.ok(ApiResult.success(reviewService.listHomeReviews()));
    }
}
