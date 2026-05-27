package com.whatsuphouse.backend.domain.application.admin.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.form.entity.ApplicationAnswer;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
public class AdminApplicationDetailResponse {

    private UUID id;
    private String bookingNumber;
    private String name;
    private String phone;
    private Gender gender;
    private Integer age;
    private String instagramId;
    private String job;
    private Mbti mbti;
    private String intro;
    private String referrerName;
    private ApplicationStatus status;
    private UUID gatheringId;
    private UUID userId;
    private LocalDateTime createdAt;
    private List<AnswerDetail> answers;

    @Getter
    @Builder
    public static class AnswerDetail {
        private String questionKey;
        private String label;
        private Object value;
        private String displayValue;
    }

    public static AdminApplicationDetailResponse from(Application application,
                                                       List<ApplicationAnswer> answers) {
        return AdminApplicationDetailResponse.builder()
                .id(application.getId())
                .bookingNumber(application.getBookingNumber())
                .name(application.getName())
                .phone(application.getPhone())
                .gender(application.getGender())
                .age(application.getAge())
                .instagramId(application.getInstagramId())
                .job(application.getJob())
                .mbti(application.getMbti())
                .intro(application.getIntro())
                .referrerName(application.getReferrerName())
                .status(application.getStatus())
                .gatheringId(application.getGathering().getId())
                .userId(application.getUser() != null ? application.getUser().getId() : null)
                .createdAt(application.getCreatedAt())
                .answers(buildAnswers(application, answers))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<AnswerDetail> buildAnswers(Application application,
                                                    List<ApplicationAnswer> answers) {
        Map<String, Object> snapshot = application.getFormSnapshot();
        if (snapshot == null) {
            return List.of();
        }

        List<Map<String, Object>> questions = (List<Map<String, Object>>) snapshot.get("questions");
        if (questions == null) {
            return List.of();
        }

        Map<String, Object> answerByQuestionId = answers.stream()
                .collect(Collectors.toMap(
                        a -> a.getQuestion().getId().toString(),
                        a -> {
                            Map<String, Object> v = a.getValue();
                            return v != null ? v.getOrDefault("value", "") : "";
                        }
                ));

        return questions.stream().map(q -> {
            String questionId = (String) q.get("questionId");
            String questionKey = (String) q.get("questionKey");
            String label = (String) q.get("label");
            Object value = answerByQuestionId.get(questionId);
            String displayValue = toDisplayValue(value);

            return AnswerDetail.builder()
                    .questionKey(questionKey)
                    .label(label)
                    .value(value)
                    .displayValue(displayValue)
                    .build();
        }).toList();
    }

    private static String toDisplayValue(Object value) {
        if (value == null) return "";
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.joining(", "));
        }
        return value.toString();
    }
}
