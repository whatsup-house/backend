package com.whatsuphouse.backend.domain.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class AdminDepositNotificationPageResponse {

    @Schema(description = "입금 알림 목록(최신순)")
    private List<AdminDepositNotificationResponse> content;
    @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
    private int page;
    @Schema(description = "페이지 크기", example = "20")
    private int size;
    @Schema(description = "전체 건수", example = "42")
    private long totalElements;
    @Schema(description = "전체 페이지 수", example = "3")
    private int totalPages;

    public static AdminDepositNotificationPageResponse from(Page<AdminDepositNotificationResponse> pageResult) {
        return AdminDepositNotificationPageResponse.builder()
                .content(pageResult.getContent())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }
}
