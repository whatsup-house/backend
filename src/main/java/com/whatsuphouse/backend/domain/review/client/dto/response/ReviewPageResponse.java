package com.whatsuphouse.backend.domain.review.client.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class ReviewPageResponse {

    @Schema(description = "리뷰 목록")
    private List<ReviewResponse> content;

    @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
    private int page;

    @Schema(description = "페이지 크기", example = "10")
    private int size;

    @Schema(description = "전체 리뷰 수", example = "20")
    private long totalElements;

    @Schema(description = "전체 페이지 수", example = "2")
    private int totalPages;

    public static ReviewPageResponse from(Page<ReviewResponse> pageResult) {
        return ReviewPageResponse.builder()
                .content(pageResult.getContent())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }
}
