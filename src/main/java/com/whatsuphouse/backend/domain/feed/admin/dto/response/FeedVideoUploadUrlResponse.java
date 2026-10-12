package com.whatsuphouse.backend.domain.feed.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class FeedVideoUploadUrlResponse {

    @Schema(description = "PUT 업로드 URL. Content-Type: video/mp4 헤더로 요청해야 합니다.",
            example = "https://<account>.r2.cloudflarestorage.com/whatsup-media/feed/videos/550e8400-e29b-41d4-a716-446655440000.mp4?X-Amz-Algorithm=...")
    private String uploadUrl;

    @Schema(description = "업로드 후 피드 게시물 media.url 에 넣을 공개 URL",
            example = "https://media.whatsup.house/feed/videos/550e8400-e29b-41d4-a716-446655440000.mp4")
    private String videoUrl;

    @Schema(description = "uploadUrl 만료 시각 (UTC)", example = "2026-10-12T03:10:00Z")
    private Instant expiresAt;
}
