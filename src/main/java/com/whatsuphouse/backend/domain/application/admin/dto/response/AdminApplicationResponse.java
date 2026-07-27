package com.whatsuphouse.backend.domain.application.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsuphouse.backend.domain.application.client.dto.response.AnswerView;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.PaymentStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.participant.enums.ParticipantAccountStatus;
import com.whatsuphouse.backend.domain.participant.enums.ParticipantType;
import com.whatsuphouse.backend.domain.participant.enums.RandomTableEligibility;
import com.whatsuphouse.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Builder
public class AdminApplicationResponse {

    private UUID id;
    private String bookingNumber;
    private String name;
    private String phone;
    private String gender;
    private Integer age;
    private String job;
    private String mbti;
    private String intro;
    private String referralSource;
    private ApplicationStatus status;
    private boolean paid;               // 유료 게더링 여부 (입금 컬럼 노출 대상인지)
    private boolean free;               // 참가비 0원 여부
    private PaymentStatus paymentStatus;
    private boolean paymentConfirmed;   // 입금 확인 여부
    private LocalDateTime paymentConfirmedAt;
    private UUID gatheringId;
    private GatheringType gatheringType;
    private UUID userId;
    private UUID participantId;
    private ParticipantType participantType;
    private ParticipantAccountStatus participantAccountStatus;
    private RandomTableEligibility randomTableEligibility;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private LocalDateTime createdAt;
    @JsonProperty("isGuest")
    private boolean isGuest;
    private List<AnswerView> answers;

    public static AdminApplicationResponse from(Application application) {
        return from(application, null);
    }

    public static AdminApplicationResponse from(Application application, List<AnswerView> answers) {
        User user = application.getUser();
        Participant participant = application.getParticipant();
        return AdminApplicationResponse.builder()
                .id(application.getId())
                .bookingNumber(application.getBookingNumber())
                .name(application.getName())
                .phone(application.getPhone())
                .gender(firstString(answers, "gender", user != null && user.getGender() != null ? user.getGender().name() : null))
                .age(firstInteger(answers, "age", user != null ? user.getCurrentAge() : null))
                .job(firstString(answers, "job", firstString(answers, "job_category", user != null ? user.getJob() : null)))
                .mbti(firstString(answers, "mbti", user != null && user.getMbti() != null ? user.getMbti().name() : null))
                .intro(firstString(answers, "intro", user != null ? user.getIntro() : null))
                .referralSource(firstString(answers, "referralSource", firstString(answers, "referral_source", null)))
                .status(application.getStatus())
                .paid(application.isPaidGathering())
                .free(application.isFreeGathering())
                .paymentStatus(application.getPaymentStatus())
                .paymentConfirmed(application.isPaymentConfirmed())
                .paymentConfirmedAt(application.getPaymentConfirmedAt())
                .gatheringId(application.getGathering().getId())
                .gatheringType(application.getGathering().getGatheringType())
                .userId(user != null ? user.getId() : null)
                .participantId(participant != null ? participant.getId() : null)
                .participantType(participant != null ? participant.getParticipantType() : null)
                .participantAccountStatus(participant != null ? participant.getAccountStatus() : null)
                .randomTableEligibility(participant != null ? participant.getRandomTableEligibility() : null)
                .reviewedAt(application.getReviewedAt())
                .rejectionReason(application.getRejectionReason())
                .createdAt(application.getCreatedAt())
                .isGuest(user == null)
                .answers(answers)
                .build();
    }

    private static String firstString(List<AnswerView> answers, String questionKey, String fallback) {
        Object value = findValue(answers, questionKey);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return fallback;
    }

    private static Integer firstInteger(List<AnswerView> answers, String questionKey, Integer fallback) {
        Object value = findValue(answers, questionKey);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Object findValue(List<AnswerView> answers, String questionKey) {
        if (answers == null) {
            return null;
        }
        return answers.stream()
                .filter(answer -> Objects.equals(answer.getQuestionKey(), questionKey))
                .map(AnswerView::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
