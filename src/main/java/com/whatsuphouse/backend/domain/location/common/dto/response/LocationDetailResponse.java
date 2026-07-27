package com.whatsuphouse.backend.domain.location.common.dto.response;

import com.whatsuphouse.backend.domain.location.entity.Location;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class LocationDetailResponse {

    private UUID id;
    private String name;
    private String address;
    private String mapUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LocationDetailResponse from(Location location) {
        return LocationDetailResponse.builder()
                .id(location.getId())
                .name(location.getName())
                .address(location.getAddress())
                .mapUrl(location.getMapUrl())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}
