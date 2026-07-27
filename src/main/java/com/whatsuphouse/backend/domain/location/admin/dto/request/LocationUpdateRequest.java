package com.whatsuphouse.backend.domain.location.admin.dto.request;

import com.whatsuphouse.backend.domain.location.enums.LocationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class LocationUpdateRequest {

    @Schema(example = "홍대 카페")
    @NotBlank
    private String name;

    @Schema(example = "서울 마포구 어울마당로 35")
    @NotBlank
    private String address;

    @Schema(example = "https://map.kakao.com/link/map/12345678")
    private String mapUrl;

    @Schema(example = "20")
    @NotNull
    @Positive
    private Integer maxCapacity;

    @Schema(example = "INACTIVE")
    @NotNull
    private LocationStatus status;

    @Schema(example = "주차 불가, 지하철 2호선 홍대입구역 도보 5분")
    private String memo;
}
