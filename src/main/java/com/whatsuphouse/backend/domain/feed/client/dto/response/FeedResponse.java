package com.whatsuphouse.backend.domain.feed.client.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class FeedResponse {

    @Schema(description = "피드 항목 (게시 시각 최신순)")
    private List<FeedItemResponse> items;

    @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", example = "MjAyNi0xMC0wMVQxOTozMHw1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDA")
    private String nextCursor;
}
