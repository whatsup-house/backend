package com.whatsuphouse.backend.domain.matching.dto.response;

import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.matching.entity.Attendance;
import com.whatsuphouse.backend.domain.matching.entity.DiningContent;
import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import com.whatsuphouse.backend.domain.matching.enums.DiningContentKind;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * 참가자 테이블 상세. 멤버는 회원 ID·닉네임·캐릭터·MBTI·관심사만(연락처·실명·출생연도 제외, INF-06).
 * 매칭 근거는 장점 문구만 준다. 쌍별·그룹 점수와 다른 멤버의 응답 원문은 넣지 않는다(MAT-12, KAN-432).
 */
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
    @Schema(description = "집합 시각(회차 시작 10분 전). 식당이 배정되기 전이면 null", example = "2026-10-10T18:50:00")
    private LocalDateTime gatherAt;
    @Schema(description = "테이블 채팅방 ID. 아직 없으면 null", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID chatRoomId;
    @Schema(description = "이 테이블의 장점 문구 1~2개", example = "[\"대화 스타일 80% 일치하는 테이블이에요!\"]")
    private List<String> strengths;
    @Schema(description = "멤버(본인 포함), 좌석 순")
    private List<MemberView> members;
    @Schema(description = "취소 정책 안내 문구", example = "회차 전전날 23:59까지 취소하면 전액, 그 뒤 전날까지는 50% 환불돼요. 당일 취소와 노쇼는 환불되지 않아요.")
    private String cancelPolicy;
    @Schema(description = "내 참석. 참석 기록이 없으면 null")
    private AttendanceView myAttendance;
    @Schema(description = "채팅방에 올린 대화 주제·아이스브레이킹(올린 순). 올리기 전이면 빈 배열")
    private List<ContentView> contents;

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
        @Schema(description = "예약자명", example = "와썹하우스")
        private String reservationName;

        public static VenueView of(Venue venue, String reservationName) {
            return VenueView.builder()
                    .name(venue.getName())
                    .address(venue.getAddress())
                    .mapUrl(venue.getMapUrl())
                    .priceRange(venue.getPriceRange())
                    .reservationName(reservationName)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ContentView {
        @Schema(description = "종류(TOPIC|ICEBREAKER)", example = "TOPIC")
        private DiningContentKind kind;
        @Schema(description = "본문", example = "요즘 가장 기다려지는 일은 무엇인가요?")
        private String body;

        public static ContentView from(DiningContent content) {
            return ContentView.builder().kind(content.getKind()).body(content.getBody()).build();
        }
    }

    @Getter
    @Builder
    public static class MemberView {
        @Schema(description = "회원 ID(피드백 선호·신고 대상 지정용)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID userId;
        @Schema(description = "닉네임", example = "와썹러")
        private String nickname;
        @Schema(description = "직업 캐릭터 이미지 URL", example = "https://example.supabase.co/storage/v1/object/public/assets/characters/IT_DEV.png")
        private String characterUrl;
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
