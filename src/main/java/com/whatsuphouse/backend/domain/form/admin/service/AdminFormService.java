package com.whatsuphouse.backend.domain.form.admin.service;

import com.whatsuphouse.backend.domain.form.admin.dto.request.FormQuestionCreateRequest;
import com.whatsuphouse.backend.domain.form.admin.dto.request.FormQuestionUpdateRequest;
import com.whatsuphouse.backend.domain.form.admin.dto.response.FormQuestionResponse;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.entity.GatheringForm;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.GatheringFormRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminFormService {

    private final GatheringRepository gatheringRepository;
    private final GatheringFormRepository gatheringFormRepository;
    private final FormQuestionRepository formQuestionRepository;

    @Transactional
    public FormQuestionResponse addQuestion(UUID gatheringId, FormQuestionCreateRequest request) {
        validate(request.getType(), request.getOptions(), request.isMatchingField(), request.getMatchingStrategy());

        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));

        GatheringForm form = gatheringFormRepository
                .findByGathering_IdAndDeletedAtIsNull(gatheringId)
                .orElseGet(() -> gatheringFormRepository.save(
                        GatheringForm.builder()
                                .gathering(gathering)
                                .isActive(true)
                                .build()));

        BigDecimal weight = request.getMatchingWeight() != null
                ? request.getMatchingWeight()
                : BigDecimal.ONE;

        FormQuestion question = FormQuestion.builder()
                .form(form)
                .questionKey(request.getQuestionKey())
                .type(request.getType())
                .label(request.getLabel())
                .placeholder(request.getPlaceholder())
                .required(request.getRequired())
                .displayOrder(request.getDisplayOrder())
                .options(request.getOptions())
                .validation(request.getValidation())
                .isMatchingField(request.isMatchingField())
                .matchingStrategy(request.getMatchingStrategy())
                .matchingWeight(weight)
                .build();

        return FormQuestionResponse.from(formQuestionRepository.save(question));
    }

    @Transactional
    public FormQuestionResponse updateQuestion(UUID questionId, FormQuestionUpdateRequest request) {
        validate(request.getType(), request.getOptions(), request.isMatchingField(), request.getMatchingStrategy());

        FormQuestion question = formQuestionRepository.findById(questionId)
                .filter(q -> q.getDeletedAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.QUESTION_NOT_FOUND));

        BigDecimal weight = request.getMatchingWeight() != null
                ? request.getMatchingWeight()
                : BigDecimal.ONE;

        question.update(
                request.getQuestionKey(), request.getType(), request.getLabel(),
                request.getPlaceholder(), request.getRequired(), request.getDisplayOrder(),
                request.getOptions(), request.getValidation(), request.isMatchingField(),
                request.getMatchingStrategy(), weight);

        return FormQuestionResponse.from(question);
    }

    @Transactional
    public void deleteQuestion(UUID questionId) {
        FormQuestion question = formQuestionRepository.findById(questionId)
                .filter(q -> q.getDeletedAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.QUESTION_NOT_FOUND));
        question.softDelete();
    }

    private void validate(QuestionType type, java.util.Map<String, Object> options,
                          boolean isMatchingField, com.whatsuphouse.backend.domain.form.enums.MatchingStrategy matchingStrategy) {
        if ((type == QuestionType.SINGLE_CHOICE || type == QuestionType.MULTI_CHOICE)
                && (options == null || options.isEmpty())) {
            throw new CustomException(ErrorCode.OPTIONS_REQUIRED);
        }
        if (isMatchingField && matchingStrategy == null) {
            throw new CustomException(ErrorCode.MATCHING_STRATEGY_REQUIRED);
        }
    }
}
