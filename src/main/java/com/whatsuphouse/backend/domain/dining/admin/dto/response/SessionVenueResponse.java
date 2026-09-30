package com.whatsuphouse.backend.domain.dining.admin.dto.response;

import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SessionVenueResponse {

    @Schema(description = "식당")
    private VenueResponse venue;

    @Schema(description = "수용 테이블 수", example = "3")
    private int capacityTables;

    @Schema(description = "배정된 테이블 수", example = "1")
    private int usedTables;

    public static SessionVenueResponse of(SessionVenue sessionVenue, Venue venue) {
        return SessionVenueResponse.builder()
                .venue(VenueResponse.from(venue))
                .capacityTables(sessionVenue.getCapacityTables())
                .usedTables(sessionVenue.getUsedTables())
                .build();
    }
}
