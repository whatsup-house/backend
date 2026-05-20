package com.whatsuphouse.backend.domain.home.controller;

import com.whatsuphouse.backend.domain.carousel.client.service.CarouselService;
import com.whatsuphouse.backend.domain.carousel.common.dto.response.CarouselSlideResponse;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "캐러셀", description = "홈 캐러셀 슬라이드 API")
@RestController
@RequestMapping("/api/home/carousel")
@RequiredArgsConstructor
public class CarouselController {

    private final CarouselService carouselService;

    @Operation(summary = "활성 슬라이드 목록 조회", description = "홈 화면에 표시할 활성 캐러셀 슬라이드 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResult<List<CarouselSlideResponse>>> listActiveSlides() {
        return ResponseEntity.ok(ApiResult.success(carouselService.listActiveSlides()));
    }
}
