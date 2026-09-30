package com.whatsuphouse.backend.domain.dining.admin.dto.response;

import com.whatsuphouse.backend.domain.dining.entity.Venue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class TableVenueResponse {

    @Schema(description = "테이블 ID(현재는 매칭 그룹 ID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID tableId;

    @Schema(description = "배정된 식당")
    private VenueResponse venue;

    public static TableVenueResponse of(UUID tableId, Venue venue) {
        return TableVenueResponse.builder()
                .tableId(tableId)
                .venue(VenueResponse.from(venue))
                .build();
    }
}
