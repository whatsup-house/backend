package com.whatsuphouse.backend.domain.review.client.dto.response;

import com.whatsuphouse.backend.domain.review.enums.ReviewSort;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReviewLocateResponse {

    @Schema(description = "리뷰가 위치한 페이지 번호 (0부터 시작)", example = "2")
    private int page;

    @Schema(description = "페이지 크기", example = "10")
    private int size;

    @Schema(description = "정렬 기준", example = "LIKES")
    private ReviewSort sort;

    public static ReviewLocateResponse of(int page, int size, ReviewSort sort) {
        return ReviewLocateResponse.builder()
                .page(page)
                .size(size)
                .sort(sort)
                .build();
    }
}
