package com.whatsuphouse.backend.domain.review.admin.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class AdminReviewPageResponse {

    private List<AdminReviewResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static AdminReviewPageResponse from(Page<AdminReviewResponse> pageResult) {
        return AdminReviewPageResponse.builder()
                .content(pageResult.getContent())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }
}
