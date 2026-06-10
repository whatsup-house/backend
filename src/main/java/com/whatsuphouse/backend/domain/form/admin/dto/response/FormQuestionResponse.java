package com.whatsuphouse.backend.domain.form.admin.dto.response;

import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.MatchingStrategy;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class FormQuestionResponse {

    private UUID questionId;
    private String questionKey;
    private QuestionType type;
    private String label;
    private String placeholder;
    private boolean required;
    private int displayOrder;
    private Map<String, Object> options;
    private Map<String, Object> validation;
    private boolean isMatchingField;
    private boolean systemReserved;
    private MatchingStrategy matchingStrategy;
    private BigDecimal matchingWeight;

    public static FormQuestionResponse from(FormQuestion question) {
        return FormQuestionResponse.builder()
                .questionId(question.getId())
                .questionKey(question.getQuestionKey())
                .type(question.getType())
                .label(question.getLabel())
                .placeholder(question.getPlaceholder())
                .required(question.isRequired())
                .displayOrder(question.getDisplayOrder())
                .options(question.getOptions())
                .validation(question.getValidation())
                .isMatchingField(question.isMatchingField())
                .systemReserved(question.isSystemReserved())
                .matchingStrategy(question.getMatchingStrategy())
                .matchingWeight(question.getMatchingWeight())
                .build();
    }
}
