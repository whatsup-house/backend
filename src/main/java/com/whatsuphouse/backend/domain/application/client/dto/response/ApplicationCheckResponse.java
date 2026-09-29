package com.whatsuphouse.backend.domain.application.client.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.PaymentStatus;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ApplicationCheckResponse {

    private UUID id;
    private String bookingNumber;
    private String name;
    private String phone;
    private ApplicationStatus status;
    // 입금 상태. 무료 게더링은 null(표시하지 않음). (KAN-242)
    private PaymentStatus paymentStatus;
    // 우연한 식탁에서 이 신청으로 이용권 1회가 사용된 뒤 남은 회차. 미사용/비대상은 null.
    private Integer ticketRemainingCount;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private GatheringInfo gathering;
    private LocalDateTime createdAt;
    private List<AnswerView> answers;

    @Getter
    @Builder
    public static class GatheringInfo {
        private UUID id;
        private String title;
        private String eventDate;
        private String startTime;
    }

    public static ApplicationCheckResponse from(Application application, List<AnswerView> answers) {
        return from(application, answers, null);
    }

    public static ApplicationCheckResponse from(Application application, List<AnswerView> answers, Integer ticketRemainingCount) {
        GatheringSession session = application.getSession();
        return ApplicationCheckResponse.builder()
                .id(application.getId())
                .bookingNumber(application.getBookingNumber())
                .name(application.getName())
                .phone(application.getPhone())
                .status(application.getStatus())
                .paymentStatus(application.getPaymentStatus())
                .ticketRemainingCount(ticketRemainingCount)
                .reviewedAt(application.getReviewedAt())
                .rejectionReason(application.getRejectionReason())
                // 기존 API 호환: gathering.id는 회차 ID, 날짜·시간은 회차 값. (KAN-337)
                // 회차 배정 전(우연한 식탁 매칭 전)이면 id는 종류 ID, 날짜·시간은 null. (KAN-338)
                .gathering(GatheringInfo.builder()
                        .id(application.getLegacyGatheringId())
                        .title(application.getGathering().getTitle())
                        .eventDate(session != null ? session.getEventDate().toString() : null)
                        .startTime(session != null && session.getStartTime() != null
                                ? session.getStartTime().toString() : null)
                        .build())
                .createdAt(application.getCreatedAt())
                .answers(answers)
                .build();
    }
}
