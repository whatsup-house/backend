package com.whatsuphouse.backend.domain.feed.admin.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class AdminFeedPostPageResponse {

    private List<AdminFeedPostResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static AdminFeedPostPageResponse from(Page<AdminFeedPostResponse> pageResult) {
        return AdminFeedPostPageResponse.builder()
                .content(pageResult.getContent())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }
}
