package com.whatsuphouse.backend.domain.form.admin.dto.request;

import com.whatsuphouse.backend.domain.form.enums.MatchingStrategy;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
public class FormQuestionCreateRequest {

    @NotBlank
    private String questionKey;

    @NotNull
    private QuestionType type;

    @NotBlank
    private String label;

    private String placeholder;

    @NotNull
    private Boolean required;

    @NotNull
    private Integer displayOrder;

    private Map<String, Object> options;

    private Map<String, Object> validation;

    private boolean isMatchingField = false;

    private String matchingKey;

    private MatchingStrategy matchingStrategy;

    private BigDecimal matchingWeight;
}
