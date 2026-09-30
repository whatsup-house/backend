package com.whatsuphouse.backend.domain.dining.admin.dto.request;

import com.whatsuphouse.backend.domain.dining.entity.Venue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 식당 생성·수정 공용(수정은 전체 교체). */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VenueRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(description = "식당 이름", example = "을지로 한식당")
    private String name;

    @NotBlank
    @Size(max = 255)
    @Schema(description = "주소", example = "서울 중구 을지로 123")
    private String address;

    // 화면에서 링크로 열리므로 http(s)만 받는다.
    @Size(max = 500)
    @Pattern(regexp = "^https?://\\S+$", message = "지도 링크는 http(s) 주소여야 합니다.")
    @Schema(description = "지도 링크", example = "https://naver.me/abcd1234", nullable = true)
    private String mapUrl;

    @Size(max = 50)
    @Schema(description = "가격대", example = "1~2만원", nullable = true)
    private String priceRange;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "지역", example = "을지로")
    private String region;

    @NotNull
    @Schema(description = "활성 여부. 비활성 식당은 새로 배정할 수 없다", example = "true")
    private Boolean isActive;

    public Venue toEntity() {
        return Venue.builder()
                .name(name)
                .address(address)
                .mapUrl(mapUrl)
                .priceRange(priceRange)
                .region(region)
                .isActive(isActive)
                .build();
    }
}
