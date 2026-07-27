package com.whatsuphouse.backend.domain.review.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewHomeOrderItemRequest {

    @NotNull
    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID reviewId;

    @NotNull
    @Min(value = 0, message = "홈 노출 순서는 0 이상이어야 합니다.")
    @Schema(example = "1")
    private Integer homeDisplayOrder;
}
