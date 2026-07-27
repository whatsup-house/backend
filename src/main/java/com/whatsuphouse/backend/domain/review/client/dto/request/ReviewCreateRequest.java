package com.whatsuphouse.backend.domain.review.client.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class ReviewCreateRequest {

    @NotNull
    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID applicationId;

    @NotBlank
    @Size(min = 10, message = "리뷰 내용은 10자 이상이어야 합니다.")
    @Schema(example = "모임이 정말 좋았고 다음에도 참여하고 싶어요.")
    private String reviewContent;

    @Size(max = 10, message = "리뷰 이미지는 최대 10개까지 등록할 수 있습니다.")
    @Schema(example = "[\"temp/review/550e8400-e29b-41d4-a716-446655440000.jpg\"]")
    private List<@NotBlank String> imageTempPaths;
}
