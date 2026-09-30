package com.whatsuphouse.backend.domain.dining.client.dto.response;

import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/** 내 우연한 식탁 참가 이력 한 건(확정·완료 테이블 멤버십). GET /api/dining/me/history (KAN-350) */
@Getter
@Builder
public class DiningHistoryResponse {

    @Schema(description = "테이블 ID", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    private UUID tableId;

    @Schema(description = "회차 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID sessionId;

    @Schema(description = "행사 날짜", example = "2026-10-10")
    private LocalDate eventDate;

    @Schema(description = "지역(회차 장소명). 장소가 없으면 null", example = "강남", nullable = true)
    private String region;

    @Schema(description = "배정 식당 이름. 배정 전이면 null", example = "을지로 한식당", nullable = true)
    private String venueName;

    @Schema(description = "테이블 상태 (CONFIRMED: 확정, DONE: 종료)", example = "DONE")
    private DiningTableStatus tableStatus;

    @Schema(description = "피드백 제출 여부", example = "false")
    private boolean feedbackSubmitted;

    // TODO(KAN-349): 참석 상태(Attendance)로 채운다. 지금은 항상 null.
    @Schema(description = "참석 상태. 아직 제공하지 않아 항상 null", nullable = true)
    private String attendanceStatus;
}
