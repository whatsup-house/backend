package com.whatsuphouse.backend.domain.form.admin.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("isMatchingField")
    private boolean isMatchingField = false;

    private MatchingStrategy matchingStrategy;

    private BigDecimal matchingWeight;
}
