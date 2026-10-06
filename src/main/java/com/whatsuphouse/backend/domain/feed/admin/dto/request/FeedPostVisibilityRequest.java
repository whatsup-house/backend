package com.whatsuphouse.backend.domain.feed.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedPostVisibilityRequest {

    @NotNull
    @Schema(example = "true")
    private Boolean visible;
}
