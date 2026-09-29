package com.whatsuphouse.backend.domain.gathering.common.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.location.entity.Location;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** 모임 회차 하나. 종류 응답의 sessions 항목. (KAN-338) */
@Getter
@Builder
public class GatheringSessionResponse {

    @Schema(description = "회차 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @Schema(description = "행사 날짜", example = "2026-10-10")
    private LocalDate eventDate;

    @Schema(description = "시작 시간", example = "19:00:00")
    private LocalTime startTime;

    @Schema(description = "종료 시간", example = "21:00:00")
    private LocalTime endTime;

    @Schema(description = "장소 (없으면 null)")
    private LocationDetail location;

    @Schema(description = "최대 참석자 수", example = "10")
    private int maxAttendees;

    @Schema(description = "회차 가격. 회차 가격 오버라이드가 없으면 종류 기본 가격", example = "15000")
    private Integer price;

    @Schema(description = "신청 마감 시각 (없으면 마감 없음)", example = "2026-10-09T23:59:00")
    private LocalDateTime applyDeadlineAt;

    @Schema(description = "회차 상태. 날짜가 지난 모집중 회차는 DONE", example = "OPEN")
    private GatheringSessionStatus status;

    @Schema(description = "정원을 차지한 인원(승인 + 출석)", example = "3")
    private long confirmedCount;

    public static GatheringSessionResponse from(GatheringSession session, long confirmedCount) {
        Location location = session.getLocation();
        return GatheringSessionResponse.builder()
                .id(session.getId())
                .eventDate(session.getEventDate())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .location(location != null ? LocationDetail.from(location) : null)
                .maxAttendees(session.getMaxAttendees())
                .price(session.getEffectivePrice())
                .applyDeadlineAt(session.getApplyDeadlineAt())
                .status(session.getEffectiveSessionStatus())
                .confirmedCount(confirmedCount)
                .build();
    }

    @Getter
    @Builder
    public static class LocationDetail {
        @Schema(description = "장소 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID id;
        @Schema(description = "장소명", example = "카페 재즈")
        private String name;
        @Schema(description = "주소", example = "서울시 마포구 합정동 123")
        private String address;
        @Schema(description = "네이버 지도 URL", example = "https://naver.me/abcd1234")
        private String naverMapUrl;
        @Schema(description = "카카오 지도 URL", example = "https://kko.kakao.com/xyz789")
        private String kakaoMapUrl;

        static LocationDetail from(Location location) {
            return LocationDetail.builder()
                    .id(location.getId())
                    .name(location.getName())
                    .address(location.getAddress())
                    .naverMapUrl(location.getNaverMapUrl())
                    .kakaoMapUrl(location.getKakaoMapUrl())
                    .build();
        }
    }
}
