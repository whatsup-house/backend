package com.whatsuphouse.backend.domain.gathering.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

// 모임 종류 필드. 날짜·장소·정원 등 회차 필드는 GatheringSessionRequest. (KAN-338)
@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class GatheringBaseRequest {

    @Schema(example = "4월 홍대 소셜 게더링")
    @NotBlank
    private String title;

    @Schema(example = "편안하게 대화 나누는 소규모 모임입니다.")
    private String description;

    @Schema(example = "[\"아이스브레이킹\", \"식사와 대화\", \"마무리 인사\"]")
    private List<String> howToRun;

    @Schema(example = "[\"취미\", \"2030\", \"소규모\"]", description = "게더링 태그 목록 (선택)")
    private List<String> tags;

    @Schema(example = "15000", description = "기본 가격. 회차에서 priceOverride로 덮어쓸 수 있다")
    @PositiveOrZero
    private Integer basePrice;

    @Schema(example = "https://example.com/thumbnail.jpg")
    private String thumbnailUrl;

    // 상세 사진. 생략(null)=기존 유지, 빈 배열=모두 삭제. 순서 = 노출 순서. (KAN-371)
    @Schema(example = "[\"temp/gathering/a1b2.jpg\", \"https://example.com/detail-2.jpg\"]",
            description = "상세 사진 목록 (선택, 최대 10장). temp/ 경로는 저장 시 정식 경로로 옮긴다. 생략=기존 유지, 빈 배열=모두 삭제")
    @Size(max = 10)
    private List<@NotBlank @Size(max = 500) String> imageUrls;
}
