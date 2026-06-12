package com.whatsuphouse.backend.domain.application.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsuphouse.backend.domain.application.client.dto.response.AnswerView;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
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
    private boolean paymentConfirmed;   // 입금 확인 여부
    private LocalDateTime paymentConfirmedAt;
    private UUID gatheringId;
    private UUID userId;
    private LocalDateTime createdAt;
    @JsonProperty("isGuest")
    private boolean isGuest;
    private List<AnswerView> answers;

    public static AdminApplicationResponse from(Application application) {
        return from(application, null);
    }

    public static AdminApplicationResponse from(Application application, List<AnswerView> answers) {
        User user = application.getUser();
        return AdminApplicationResponse.builder()
                .id(application.getId())
                .bookingNumber(application.getBookingNumber())
                .name(application.getName())
                .phone(application.getPhone())
                .gender(firstString(answers, "gender", user != null && user.getGender() != null ? user.getGender().name() : null))
                .age(firstInteger(answers, "age", user != null ? user.getAge() : null))
                .job(firstString(answers, "job", firstString(answers, "job_category", user != null ? user.getJob() : null)))
                .mbti(firstString(answers, "mbti", user != null && user.getMbti() != null ? user.getMbti().name() : null))
                .intro(firstString(answers, "intro", user != null ? user.getIntro() : null))
                .referralSource(firstString(answers, "referralSource", firstString(answers, "referral_source", null)))
                .status(application.getStatus())
                .paid(application.isPaidGathering())
                .paymentConfirmed(application.isPaymentConfirmed())
                .paymentConfirmedAt(application.getPaymentConfirmedAt())
                .gatheringId(application.getGathering().getId())
                .userId(user != null ? user.getId() : null)
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
