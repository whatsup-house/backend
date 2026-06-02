package com.whatsuphouse.backend.domain.form.client.dto.response;

import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class GatheringFormResponse {

    private UUID formId;
    private UUID gatheringId;
    private String guideText;
    private List<QuestionDetail> questions;

    public static GatheringFormResponse from(Form form, List<FormQuestion> questions) {
        return GatheringFormResponse.builder()
                .formId(form.getId())
                .gatheringId(form.getGathering() != null ? form.getGathering().getId() : null)
                .guideText(form.getGuideText())
                .questions(questions.stream().map(QuestionDetail::from).toList())
                .build();
    }

    @Getter
    @Builder
    public static class QuestionDetail {
        private UUID questionId;
        private String questionKey;
        private QuestionType type;
        private String label;
        private String placeholder;
        private boolean required;
        private int displayOrder;
        private Map<String, Object> options;
        private Map<String, Object> validation;

        public static QuestionDetail from(FormQuestion question) {
            return QuestionDetail.builder()
                    .questionId(question.getId())
                    .questionKey(question.getQuestionKey())
                    .type(question.getType())
                    .label(question.getLabel())
                    .placeholder(question.getPlaceholder())
                    .required(question.isRequired())
                    .displayOrder(question.getDisplayOrder())
                    .options(question.getOptions())
                    .validation(question.getValidation())
                    .build();
        }
    }
}
