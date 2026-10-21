package com.whatsuphouse.backend.domain.matching.dto.response;

import com.whatsuphouse.backend.domain.matching.entity.NoticeLog;
import com.whatsuphouse.backend.domain.matching.enums.NoticeChannel;
import com.whatsuphouse.backend.domain.matching.enums.NoticeKind;
import com.whatsuphouse.backend.domain.matching.enums.NoticeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** 회차 안내 발송 이력 한 줄. GET /api/admin/dining/sessions/{id}/notices (KAN-437) */
@Getter
@Builder
public class NoticeLogResponse {

    @Schema(description = "테이블 ID. 회차 단위 안내면 null", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", nullable = true)
    private UUID tableId;

    @Schema(description = "테이블 표시 이름(회차 테이블 생성 순 번호). 해체된 테이블·회차 단위 안내면 null", example = "테이블 1", nullable = true)
    private String tableLabel;

    @Schema(description = "종류(CONFIRM_MESSAGE|REMINDER|CONTENTS|NOTIFICATION)", example = "CONFIRM_MESSAGE")
    private NoticeKind kind;

    @Schema(description = "채널(CHAT|IN_APP|PUSH)", example = "CHAT")
    private NoticeChannel channel;

    @Schema(description = "결과(SUCCESS|FAILED)", example = "SUCCESS")
    private NoticeResult status;

    @Schema(description = "같은 테이블·종류·채널의 몇 번째 시도인지(1부터)", example = "1")
    private int attempt;

    @Schema(description = "실패 사유. 성공이면 null", example = "채팅방 생성 실패", nullable = true)
    private String error;

    @Schema(description = "시도 시각", example = "2026-10-21T12:00:00")
    private LocalDateTime at;

    public static NoticeLogResponse of(NoticeLog log, String tableLabel) {
        return NoticeLogResponse.builder()
                .tableId(log.getTableId())
                .tableLabel(tableLabel)
                .kind(log.getKind())
                .channel(log.getChannel())
                .status(log.getStatus())
                .attempt(log.getAttempt())
                .error(log.getError())
                .at(log.getCreatedAt())
                .build();
    }
}
