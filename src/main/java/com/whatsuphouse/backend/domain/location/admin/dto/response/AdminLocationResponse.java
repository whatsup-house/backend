package com.whatsuphouse.backend.domain.location.admin.dto.response;

import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.enums.LocationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class AdminLocationResponse {

    private UUID id;
    private String name;
    private String address;
    private String naverMapUrl;
    private String kakaoMapUrl;
    private int maxCapacity;
    private LocationStatus status;
    private String memo;
    private LocalDateTime createdAt;

    public static AdminLocationResponse from(Location location) {
        return AdminLocationResponse.builder()
                .id(location.getId())
                .name(location.getName())
                .address(location.getAddress())
                .naverMapUrl(location.getNaverMapUrl())
                .kakaoMapUrl(location.getKakaoMapUrl())
                .maxCapacity(location.getMaxCapacity())
                .status(location.getStatus())
                .memo(location.getMemo())
                .createdAt(location.getCreatedAt())
                .build();
    }
}
