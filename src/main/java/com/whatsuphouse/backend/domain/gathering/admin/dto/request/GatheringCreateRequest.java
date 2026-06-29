package com.whatsuphouse.backend.domain.gathering.admin.dto.request;

import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class GatheringCreateRequest extends GatheringBaseRequest {

    @Schema(example = "REGULAR", description = "REGULAR(일반) 또는 RANDOM_TABLE(우연한 식탁). 미지정 시 REGULAR")
    private GatheringType gatheringType;
}
