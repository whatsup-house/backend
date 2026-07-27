package com.whatsuphouse.backend.domain.review.client.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ReviewUpdateRequest {

    @NotBlank
    @Size(min = 10, message = "리뷰 내용은 10자 이상이어야 합니다.")
    @Schema(example = "사진을 추가해서 리뷰를 수정합니다.")
    private String reviewContent;

    @Size(max = 10, message = "리뷰 이미지는 최대 10개까지 등록할 수 있습니다.")
    @Schema(description = "전달하면 기존 이미지를 교체합니다. null이면 기존 이미지를 유지합니다.",
            example = "[\"temp/review/550e8400-e29b-41d4-a716-446655440000.jpg\"]")
    private List<@NotBlank String> imageTempPaths;
}
