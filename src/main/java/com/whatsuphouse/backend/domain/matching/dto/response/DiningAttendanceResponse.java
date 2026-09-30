package com.whatsuphouse.backend.domain.matching.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.matching.entity.Attendance;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import com.whatsuphouse.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** 회차 콘솔 참석 탭 1행(테이블 멤버 1명). (KAN-349) */
@Getter
@Builder
public class DiningAttendanceResponse {

    @Schema(description = "참석 ID(상태 변경 대상)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID attendanceId;
    @Schema(description = "테이블 멤버 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID memberId;
    @Schema(description = "테이블 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID tableId;
    @Schema(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID applicationId;
    @Schema(description = "회원 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID userId;
    @Schema(description = "닉네임(회원이 아니면 신청자 이름)", example = "와썹러")
    private String nickname;
    @Schema(description = "참석 상태(SCHEDULED|ATTENDED|CANCELED_EARLY|CANCELED_LATE|NO_SHOW)", example = "SCHEDULED")
    private AttendanceStatus status;
    @Schema(description = "본인 체크인 시각. 없으면 null", example = "2026-10-10T19:05:00")
    private LocalDateTime checkedInAt;
    @Schema(description = "노쇼 후보(종료 후 체크인 없음, 운영자 확정 전)", example = "false")
    private boolean noShowCandidate;

    public static DiningAttendanceResponse of(Attendance attendance, DiningTableMember member) {
        Application application = member.getApplication();
        User user = application.getUser();
        return DiningAttendanceResponse.builder()
                .attendanceId(attendance.getId())
                .memberId(member.getId())
                .tableId(member.getTable().getId())
                .applicationId(application.getId())
                .userId(user != null ? user.getId() : null)
                .nickname(user != null ? user.getNickname() : application.getName())
                .status(attendance.getStatus())
                .checkedInAt(attendance.getCheckedInAt())
                .noShowCandidate(attendance.isNoShowCandidate())
                .build();
    }
}
