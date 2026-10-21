package com.whatsuphouse.backend.domain.application.client.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** 매칭 대기 중 희망 회차·지역 바꾸기. 둘 다 통째로 교체한다. (KAN-436) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiningWishUpdateRequest {

    @Schema(description = "희망 회차 ID 목록. 앞일수록 우선순위가 높다. 같은 모임의 모집 중·마감 전·매칭 실행 전 회차만",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
    @NotEmpty
    @Size(max = 20)
    private List<@NotNull UUID> candidateSessionIds;

    // 비어 있으면 서비스가 REGION_REQUIRED로 돌려준다(신청과 같은 코드).
    @Schema(description = "희망 지역 라벨 목록(GET /api/dining/regions 값), 지망 순. 1개 이상 필수(중복은 하나로 본다)",
            example = "[\"홍대/합정\", \"강남/신논현\"]")
    @Size(max = 10)
    private List<String> candidateRegions;
}
