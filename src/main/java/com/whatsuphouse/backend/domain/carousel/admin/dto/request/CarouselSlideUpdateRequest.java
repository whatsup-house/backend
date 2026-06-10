package com.whatsuphouse.backend.domain.carousel.admin.dto.request;

import com.whatsuphouse.backend.domain.carousel.enums.SlideType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarouselSlideUpdateRequest {

    @NotNull
    @Schema(example = "GATHERING")
    private SlideType type;

    @NotBlank
    @Size(max = 200)
    @Schema(example = "5월 소풍 모임")
    private String title;

    @Size(max = 500)
    @Schema(example = "함께 봄나들이 떠나요")
    private String content;

    // 이미지를 변경하지 않는 수정에서는 생략 가능. 값이 있으면 새 이미지로 교체된다. (KAN-182)
    @Size(max = 500)
    @Schema(example = "temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg")
    private String tempPath;

    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID gatheringId;

    @Schema(example = "0")
    private Integer sortOrder;
}
