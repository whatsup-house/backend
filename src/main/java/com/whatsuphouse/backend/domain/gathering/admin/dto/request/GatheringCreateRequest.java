package com.whatsuphouse.backend.domain.gathering.admin.dto.request;

import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatheringCreateRequest {

    @Schema(example = "4월 홍대 소셜 게더링")
    @NotBlank
    private String title;

    @Schema(example = "REGULAR", description = "REGULAR(일반) 또는 RANDOM_TABLE(우연한 식탁). 미지정 시 REGULAR")
    private GatheringType gatheringType;

    @Schema(example = "편안하게 대화 나누는 소규모 모임입니다.")
    private String description;

    @Schema(example = "[\"아이스브레이킹\", \"식사와 대화\", \"마무리 인사\"]")
    private List<String> howToRun;

    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull
    private UUID locationId;

    @Schema(example = "2026-05-10")
    @NotNull
    private LocalDate eventDate;

    @Schema(example = "19:00:00")
    private LocalTime startTime;

    @Schema(example = "21:00:00")
    private LocalTime endTime;

    @Schema(example = "15000")
    @Positive
    private Integer price;

    @Schema(example = "10")
    @NotNull
    @Positive
    private Integer maxAttendees;

    @Schema(example = "https://example.com/thumbnail.jpg")
    private String thumbnailUrl;
}
