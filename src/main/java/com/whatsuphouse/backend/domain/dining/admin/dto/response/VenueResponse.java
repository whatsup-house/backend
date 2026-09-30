package com.whatsuphouse.backend.domain.dining.admin.dto.response;

import com.whatsuphouse.backend.domain.dining.entity.Venue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class VenueResponse {

    @Schema(description = "식당 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @Schema(description = "식당 이름", example = "을지로 한식당")
    private String name;

    @Schema(description = "주소", example = "서울 중구 을지로 123")
    private String address;

    @Schema(description = "지도 링크", example = "https://naver.me/abcd1234", nullable = true)
    private String mapUrl;

    @Schema(description = "가격대", example = "1~2만원", nullable = true)
    private String priceRange;

    @Schema(description = "지역", example = "을지로")
    private String region;

    @Schema(description = "활성 여부", example = "true")
    private Boolean isActive;

    public static VenueResponse from(Venue venue) {
        return VenueResponse.builder()
                .id(venue.getId())
                .name(venue.getName())
                .address(venue.getAddress())
                .mapUrl(venue.getMapUrl())
                .priceRange(venue.getPriceRange())
                .region(venue.getRegion())
                .isActive(venue.isActive())
                .build();
    }
}
