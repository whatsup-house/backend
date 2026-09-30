package com.whatsuphouse.backend.domain.gathering.admin.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 회차 생성. 두 형태를 받는다. (KAN-338)
 * - 단건: 회차 필드를 최상위에 둔다.
 * - 주간 반복: {base: 회차 필드, repeatWeekly: {until}}. base의 필드를 최상위로 옮겨 단건과 같은 규칙으로 검증한다.
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class GatheringSessionCreateRequest extends GatheringSessionRequest {

    @Schema(description = "주간 반복. 기준 회차 날짜부터 until까지 7일 간격으로 만든다 (선택)")
    @Valid
    private RepeatWeekly repeatWeekly;

    @Schema(description = "반복 생성 시 기준 회차 필드. 단건 생성이면 회차 필드를 최상위에 둔다")
    @JsonProperty("base")
    private void setBase(GatheringSessionRequest base) {
        if (base != null) {
            copyFrom(base);
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepeatWeekly {

        @Schema(example = "2026-12-26", description = "반복 마지막 날짜(포함). 기준 회차 날짜부터 1년 이내")
        @NotNull
        private LocalDate until;
    }
}
