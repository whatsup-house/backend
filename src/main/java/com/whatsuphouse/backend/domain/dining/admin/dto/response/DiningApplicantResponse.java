package com.whatsuphouse.backend.domain.dining.admin.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 회차 신청자 표 한 행. */
@Getter
@Builder
public class DiningApplicantResponse {

    @Schema(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID applicationId;

    @Schema(description = "회원 ID(레거시 비회원 신청은 null)", nullable = true)
    private UUID userId;

    @Schema(description = "이름", example = "홍길동")
    private String name;

    @Schema(description = "나이(신청 답변 원문)", example = "29", nullable = true)
    private String age;

    @Schema(description = "성별(신청 답변 원문)", example = "FEMALE", nullable = true)
    private String gender;

    @Schema(description = "MBTI(신청 답변 원문)", example = "ENFP", nullable = true)
    private String mbti;

    @Schema(description = "신청 상태", example = "CONFIRMED")
    private ApplicationStatus status;

    @Schema(description = "결제 완료 여부(이용권 차감 완료 = 승인·출석)", example = "true")
    private Boolean isPaid;

    @Schema(description = "매칭 상태", example = "WAITING", nullable = true)
    private MatchStatus matchStatus;

    @Schema(description = "희망 회차, 1순위부터")
    private List<CandidateSession> candidateSessions;

    @Schema(description = "우연한 식탁 참가(출석) 횟수", example = "2")
    private long participationCount;

    @Schema(description = "같은 회차 신청자와 제외 관계(피하고 싶음·신고)가 있는지", example = "false")
    private Boolean hasExcludedRelation;

    public static DiningApplicantResponse of(Application application, String age, String gender, String mbti,
                                             List<ApplicationCandidateSession> candidates, long participationCount) {
        return DiningApplicantResponse.builder()
                .applicationId(application.getId())
                .userId(application.getUser() != null ? application.getUser().getId() : null)
                .name(application.getName())
                .age(age)
                .gender(gender)
                .mbti(mbti)
                .status(application.getStatus())
                .isPaid(ApplicationStatus.SEAT_OCCUPYING.contains(application.getStatus()))
                .matchStatus(application.getMatchStatus())
                .candidateSessions(candidates.stream().map(CandidateSession::from).toList())
                .participationCount(participationCount)
                // TODO(peer_preferences·safety_reports 도입 후): 같은 회차 신청자와의 AVOID·신고 관계로 채운다. 지금은 항상 false.
                .hasExcludedRelation(false)
                .build();
    }

    @Getter
    @Builder
    public static class CandidateSession {
        @Schema(description = "회차 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID sessionId;
        @Schema(description = "행사 날짜", example = "2026-10-10")
        private LocalDate eventDate;
        @Schema(description = "희망 순위(1이 1순위)", example = "1")
        private int priority;

        static CandidateSession from(ApplicationCandidateSession candidate) {
            return CandidateSession.builder()
                    .sessionId(candidate.getSession().getId())
                    .eventDate(candidate.getSession().getEventDate())
                    .priority(candidate.getPriority())
                    .build();
        }
    }
}
