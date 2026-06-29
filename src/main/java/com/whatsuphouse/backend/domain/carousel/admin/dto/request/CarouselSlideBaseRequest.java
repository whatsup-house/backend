package com.whatsuphouse.backend.domain.carousel.admin.dto.request;

import com.whatsuphouse.backend.domain.carousel.enums.SlideType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class CarouselSlideBaseRequest {

    @NotNull
    @Schema(example = "GATHERING")
    private SlideType type;

    @Size(max = 200)
    @Schema(example = "5월 소풍 모임")
    private String title;

    @Size(max = 500)
    @Schema(example = "함께 봄나들이 떠나요")
    private String content;

    @Size(max = 500)
    @Schema(example = "temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg")
    private String tempPath;

    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID gatheringId;

    @Schema(example = "0")
    private Integer sortOrder;
}
