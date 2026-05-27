package com.whatsuphouse.backend.domain.form.admin.dto.response;

import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.MatchingStrategy;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class FormQuestionResponse {

    private UUID questionId;
    private String questionKey;
    private QuestionType type;
    private String label;
    private int displayOrder;
    private boolean isMatchingField;
    private MatchingStrategy matchingStrategy;
    private BigDecimal matchingWeight;

    public static FormQuestionResponse from(FormQuestion question) {
        return FormQuestionResponse.builder()
                .questionId(question.getId())
                .questionKey(question.getQuestionKey())
                .type(question.getType())
                .label(question.getLabel())
                .displayOrder(question.getDisplayOrder())
                .isMatchingField(question.isMatchingField())
                .matchingStrategy(question.getMatchingStrategy())
                .matchingWeight(question.getMatchingWeight())
                .build();
    }
}
