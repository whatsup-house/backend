package com.whatsuphouse.backend.domain.feed.admin.dto.request;

import com.whatsuphouse.backend.domain.feed.enums.FeedMediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedMediaRequest {

    @NotNull
    @Schema(example = "IMAGE")
    private FeedMediaType type;

    // IMAGE: temp/ 경로면 정식 경로로 이동, 아니면 그대로. VIDEO: 허용 접두사(https) URL 그대로.
    @NotBlank
    @Size(max = 500)
    @Schema(example = "temp/feed/550e8400-e29b-41d4-a716-446655440000.jpg")
    private String url;

    @Size(max = 500)
    @Schema(example = "temp/feed/550e8400-e29b-41d4-a716-446655440001.jpg")
    private String posterUrl;

    @Schema(example = "1080")
    private Integer width;

    @Schema(example = "1350")
    private Integer height;
}
