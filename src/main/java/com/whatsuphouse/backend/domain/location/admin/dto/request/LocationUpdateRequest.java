package com.whatsuphouse.backend.domain.location.admin.dto.request;

import com.whatsuphouse.backend.domain.location.enums.LocationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateRequest {

    @Schema(example = "홍대 카페")
    @NotBlank
    private String name;

    @Schema(example = "서울 마포구 어울마당로 35")
    @NotBlank
    private String address;

    @Schema(description = "하위 호환용 legacy 지도 URL", example = "https://naver.me/xHgIyXJR")
    private String mapUrl;

    @Schema(description = "네이버 지도 URL", example = "https://naver.me/xHgIyXJR")
    private String naverMapUrl;

    @Schema(description = "카카오 지도 URL", example = "https://kko.kakao.com/abcdEFGH")
    private String kakaoMapUrl;

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
