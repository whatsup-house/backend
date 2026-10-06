package com.whatsuphouse.backend.domain.feed.entity;

import com.whatsuphouse.backend.domain.feed.enums.FeedMediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 피드 미디어 한 장. feed_posts.media(jsonb) 원소이자 피드 응답 media[] 원소다. (KAN-380) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FeedMedia {

    @Schema(description = "미디어 유형", example = "IMAGE")
    private FeedMediaType type;

    @Schema(description = "미디어 URL", example = "https://cdn.example.com/feed/a.jpg")
    private String url;

    @Schema(description = "영상 포스터 이미지 URL (nullable)", example = "https://cdn.example.com/feed/a-poster.jpg")
    private String posterUrl;

    @Schema(description = "가로 픽셀 (nullable)", example = "1080")
    private Integer width;

    @Schema(description = "세로 픽셀 (nullable)", example = "1350")
    private Integer height;

    public static FeedMedia image(String url) {
        return new FeedMedia(FeedMediaType.IMAGE, url, null, null, null);
    }
}
