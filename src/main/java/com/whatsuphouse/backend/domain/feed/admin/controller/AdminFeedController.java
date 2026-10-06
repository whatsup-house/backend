package com.whatsuphouse.backend.domain.feed.admin.controller;

import com.whatsuphouse.backend.domain.feed.admin.dto.request.FeedPostCreateRequest;
import com.whatsuphouse.backend.domain.feed.admin.dto.request.FeedPostVisibilityRequest;
import com.whatsuphouse.backend.domain.feed.admin.dto.response.AdminFeedPostPageResponse;
import com.whatsuphouse.backend.domain.feed.admin.dto.response.AdminFeedPostResponse;
import com.whatsuphouse.backend.domain.feed.admin.service.AdminFeedService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "피드 관리 (관리자)", description = "관리자 피드 게시물 CRUD API")
@RestController
@RequestMapping("/api/admin/feed")
@RequiredArgsConstructor
public class AdminFeedController {

    private final AdminFeedService adminFeedService;

    @Operation(summary = "피드 게시물 목록 조회", description = "숨김 포함, 인스타 게시 시각 최신순. 관리자 권한이 필요합니다.")
    @GetMapping
    public ResponseEntity<ApiResult<AdminFeedPostPageResponse>> listFeedPosts(
            @Parameter(description = "노출 여부 필터 (생략 시 전체)")
            @RequestParam(required = false) Boolean visible,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResult.success(adminFeedService.listFeedPosts(visible, page, size)));
    }

    @Operation(summary = "피드 게시물 등록", description = "visible 생략 시 숨김으로 등록됩니다. 관리자 권한이 필요합니다.")
    @PostMapping
    public ResponseEntity<ApiResult<AdminFeedPostResponse>> createFeedPost(
            @Valid @RequestBody FeedPostCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success("피드 게시물이 등록되었습니다.", adminFeedService.createFeedPost(request)));
    }

    @Operation(summary = "피드 게시물 수정", description = "media 순서 포함 전체 덮어쓰기. 관리자 권한이 필요합니다.")
    @PutMapping("/{feedPostId}")
    public ResponseEntity<ApiResult<AdminFeedPostResponse>> updateFeedPost(
            @PathVariable UUID feedPostId,
            @Valid @RequestBody FeedPostCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success("피드 게시물이 수정되었습니다.",
                adminFeedService.updateFeedPost(feedPostId, request)));
    }

    @Operation(summary = "피드 게시물 노출 변경", description = "관리자 권한이 필요합니다.")
    @PatchMapping("/{feedPostId}/visibility")
    public ResponseEntity<ApiResult<AdminFeedPostResponse>> changeFeedPostStatus(
            @PathVariable UUID feedPostId,
            @Valid @RequestBody FeedPostVisibilityRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success("피드 게시물 노출 상태가 변경되었습니다.",
                adminFeedService.changeFeedPostStatus(feedPostId, request)));
    }

    @Operation(summary = "피드 게시물 삭제", description = "soft delete. 관리자 권한이 필요합니다.")
    @DeleteMapping("/{feedPostId}")
    public ResponseEntity<ApiResult<Void>> deleteFeedPost(@PathVariable UUID feedPostId) {
        adminFeedService.deleteFeedPost(feedPostId);
        return ResponseEntity.ok(ApiResult.success("피드 게시물이 삭제되었습니다.", null));
    }
}
