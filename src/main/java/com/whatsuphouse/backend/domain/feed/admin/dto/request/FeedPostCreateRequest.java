package com.whatsuphouse.backend.domain.feed.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 피드 게시물 생성·수정 공용 요청. 수정은 media 순서 포함 전체 덮어쓰기. (KAN-380) */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedPostCreateRequest {

    @Valid
    @NotNull
    @Size(min = 1, max = 10)
    @Schema(description = "미디어 목록 (1~10개, 배열 순서 = 노출 순서)")
    private List<@NotNull FeedMediaRequest> media;

    @Size(max = 2000)
    @Schema(example = "지난 주말 와인 나잇 현장")
    private String caption;

    @Size(max = 500)
    @Schema(example = "https://www.instagram.com/p/abc123/")
    private String instagramUrl;

    @NotNull
    @Schema(example = "2026-10-01T19:30:00")
    private LocalDateTime postedAt;

    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID gatheringId;

    @Schema(description = "노출 여부 (생략 시 false)", example = "false")
    private Boolean visible;
}
