package com.whatsuphouse.backend.domain.feed.client.controller;

import com.whatsuphouse.backend.domain.feed.client.dto.response.FeedResponse;
import com.whatsuphouse.backend.domain.feed.client.service.FeedService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "피드", description = "공개 피드 API (피드 게시물 + 포토후기)")
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    @Operation(summary = "피드 조회",
            description = "노출 중인 피드 게시물과 포토후기를 게시 시각 최신순으로 섞어 반환합니다. 인증 불필요. "
                    + "다음 페이지는 응답의 nextCursor 를 cursor 로 넘깁니다(null 이면 마지막 페이지).")
    @GetMapping
    public ResponseEntity<ApiResult<FeedResponse>> listFeed(
            @Parameter(description = "이전 응답의 nextCursor (첫 페이지는 생략)")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (최대 30)", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResult.success(feedService.listFeed(cursor, size)));
    }
}
