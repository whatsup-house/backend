package com.whatsuphouse.backend.domain.feed.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 피드 영상 직접 업로드 URL 발급 요청. 형식(video/mp4)·크기(1B~50MB) 검사는 서비스에서 한다. (KAN-379) */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedVideoUploadUrlRequest {

    @NotBlank
    @Schema(description = "영상 MIME 타입 (video/mp4만 허용)", example = "video/mp4")
    private String contentType;

    @NotNull
    @Schema(description = "파일 크기(bytes, 최대 50MB). 업로드 시 같은 크기로 PUT 해야 합니다.", example = "8388608")
    private Long size;
}
