package com.whatsuphouse.backend.domain.location.common.dto.response;

import com.whatsuphouse.backend.domain.location.entity.Location;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class LocationResponse {

    private UUID id;
    private String name;
    private String address;
    private String naverMapUrl;
    private String kakaoMapUrl;

    public static LocationResponse from(Location location) {
        return LocationResponse.builder()
                .id(location.getId())
                .name(location.getName())
                .address(location.getAddress())
                .naverMapUrl(location.getNaverMapUrl())
                .kakaoMapUrl(location.getKakaoMapUrl())
                .build();
    }
}
