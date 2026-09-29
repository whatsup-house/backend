package com.whatsuphouse.backend.domain.gathering.admin.dto.response;

import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class AdminGatheringResponse {

    @Schema(description = "회차 ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID id;

    @Schema(description = "모임 종류 ID (종류 수정·삭제·회차 추가에 쓴다)", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
    private UUID gatheringId;

    @Schema(description = "제목", example = "재즈 게더링")
    private String title;

    @Schema(description = "행사 날짜", example = "2026-05-10")
    private LocalDate eventDate;

    @Schema(description = "시작 시간", example = "19:00:00")
    private LocalTime startTime;

    @Schema(description = "종료 시간", example = "21:00:00")
    private LocalTime endTime;

    @Schema(description = "장소명", example = "카페 재즈")
    private String locationName;

    @Schema(description = "가격", example = "30000")
    private Integer price;

    @Schema(description = "최대 참석자 수", example = "20")
    private int maxAttendees;

    @Schema(description = "게더링 상태", example = "OPEN")
    private GatheringStatus status;

    @Schema(description = "전체 신청자 수 (CANCELLED 제외)", example = "10")
    private long applicantCount;

    @Schema(description = "대기 중 신청자 수", example = "5")
    private long pendingCount;

    @Schema(description = "확정 신청자 수", example = "3")
    private long confirmedCount;

    @Schema(description = "출석 신청자 수", example = "2")
    private long attendedCount;

    // 관리자 목록 항목 하나 = 회차 하나. id는 회차 ID. (KAN-337)
    public static AdminGatheringResponse from(GatheringSession session, Map<ApplicationStatus, Long> countMap) {
        long pendingCount = countMap.getOrDefault(ApplicationStatus.PENDING, 0L);
        long confirmedCount = countMap.getOrDefault(ApplicationStatus.CONFIRMED, 0L);
        long attendedCount = countMap.getOrDefault(ApplicationStatus.ATTENDED, 0L);

        String locationName = session.getLocation() != null
                ? session.getLocation().getName()
                : null;

        return AdminGatheringResponse.builder()
                .id(session.getId())
                .gatheringId(session.getGathering().getId())
                .title(session.getGathering().getTitle())
                .eventDate(session.getEventDate())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .locationName(locationName)
                .price(session.getEffectivePrice())
                .maxAttendees(session.getMaxAttendees())
                .status(session.getStatus().toGatheringStatus())
                .applicantCount(pendingCount + confirmedCount + attendedCount)
                .pendingCount(pendingCount)
                .confirmedCount(confirmedCount)
                .attendedCount(attendedCount)
                .build();
    }
}
