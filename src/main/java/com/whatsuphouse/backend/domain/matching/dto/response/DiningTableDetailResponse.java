package com.whatsuphouse.backend.domain.matching.dto.response;

import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.matching.entity.Attendance;
import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** 참가자 테이블 상세. 멤버는 회원 ID·닉네임·MBTI·관심사만(연락처·실명·출생연도 제외, INF-06). */
@Getter
@Builder
public class DiningTableDetailResponse {

    @Schema(description = "테이블 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;
    @Schema(description = "상태(CONFIRMED|DONE)", example = "CONFIRMED")
    private DiningTableStatus status;
    @Schema(description = "회차")
    private SessionView session;
    @Schema(description = "배정 식당. 아직 없으면 null")
    private VenueView venue;
    @Schema(description = "테이블 채팅방 ID. 아직 없으면 null", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID chatRoomId;
    @Schema(description = "멤버(본인 포함), 좌석 순")
    private List<MemberView> members;
    @Schema(description = "취소 정책 안내 문구", example = "회차 시작 2일 전까지 취소하면 이용권이 복원돼요.")
    private String cancelPolicy;
    @Schema(description = "내 참석. 참석 기록이 없으면 null")
    private AttendanceView myAttendance;

    @Getter
    @Builder
    public static class SessionView {
        @Schema(description = "행사일", example = "2026-10-10")
        private LocalDate eventDate;
        @Schema(description = "시작 시간", example = "19:00:00")
        private LocalTime startTime;
        @Schema(description = "종료 시간", example = "21:00:00")
        private LocalTime endTime;
        @Schema(description = "지역", example = "강남")
        private String region;
    }

    @Getter
    @Builder
    public static class VenueView {
        @Schema(description = "식당 이름", example = "와썹 비스트로")
        private String name;
        @Schema(description = "주소", example = "서울 강남구 테헤란로 1")
        private String address;
        @Schema(description = "지도 링크", example = "https://map.naver.com/p/entry/place/1")
        private String mapUrl;
        @Schema(description = "가격대", example = "1~2만원")
        private String priceRange;

        public static VenueView from(Venue venue) {
            return VenueView.builder()
                    .name(venue.getName())
                    .address(venue.getAddress())
                    .mapUrl(venue.getMapUrl())
                    .priceRange(venue.getPriceRange())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class MemberView {
        @Schema(description = "회원 ID(피드백 선호·신고 대상 지정용)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID userId;
        @Schema(description = "닉네임", example = "와썹러")
        private String nickname;
        @Schema(description = "MBTI. 답하지 않았으면 null", example = "ENFP")
        private String mbti;
        @Schema(description = "관심사", example = "[\"여행\", \"음악\"]")
        private List<String> interests;
    }

    @Getter
    @Builder
    public static class AttendanceView {
        @Schema(description = "참석 상태", example = "SCHEDULED")
        private AttendanceStatus status;
        @Schema(description = "체크인 시각", example = "2026-10-10T19:05:00")
        private LocalDateTime checkedInAt;

        public static AttendanceView from(Attendance attendance) {
            return AttendanceView.builder()
                    .status(attendance.getStatus())
                    .checkedInAt(attendance.getCheckedInAt())
                    .build();
        }
    }
}
