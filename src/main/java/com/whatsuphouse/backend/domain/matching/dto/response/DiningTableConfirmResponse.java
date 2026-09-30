package com.whatsuphouse.backend.domain.matching.dto.response;

import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** 즉시 확정 결과. 하드 조건 위반으로 보류되면 status는 PROPOSED 그대로다(예외함 CONFLICT). */
@Getter
@Builder
public class DiningTableConfirmResponse {

    @Schema(description = "테이블 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;
    @Schema(description = "처리 후 상태. 보류되면 PROPOSED", example = "CONFIRMED")
    private DiningTableStatus status;
    @Schema(description = "확정 시각", example = "2026-10-08T23:00:00")
    private LocalDateTime confirmedAt;
    @Schema(description = "배정 식당 ID. 배정 실패 시 null(예외함 VENUE)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID venueId;
    @Schema(description = "채팅방 ID. 생성 실패 시 null(예외함 NOTIFICATION)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID chatRoomId;

    public static DiningTableConfirmResponse from(DiningTable table) {
        return DiningTableConfirmResponse.builder()
                .id(table.getId())
                .status(table.getStatus())
                .confirmedAt(table.getConfirmedAt())
                .venueId(table.getVenueId())
                .chatRoomId(table.getChatRoomId())
                .build();
    }
}
