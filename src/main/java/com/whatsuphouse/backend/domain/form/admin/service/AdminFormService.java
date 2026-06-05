package com.whatsuphouse.backend.domain.form.admin.service;

import com.whatsuphouse.backend.domain.form.admin.dto.request.FormQuestionCreateRequest;
import com.whatsuphouse.backend.domain.form.admin.dto.request.FormQuestionUpdateRequest;
import com.whatsuphouse.backend.domain.form.admin.dto.response.FormQuestionResponse;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.FormRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminFormService {

    private final GatheringRepository gatheringRepository;
    private final FormRepository formRepository;
    private final FormQuestionRepository formQuestionRepository;

    // 관리자용 질문 목록 (매칭 설정 포함). 폼이 아직 없으면 빈 목록.
    public List<FormQuestionResponse> getQuestions(UUID gatheringId) {
        return formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)
                .map(form -> formQuestionRepository
                        .findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(form).stream()
                        .map(FormQuestionResponse::from)
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional
    public FormQuestionResponse addQuestion(UUID gatheringId, FormQuestionCreateRequest request) {
        validate(request.getType(), request.getOptions(), request.isMatchingField(), request.getMatchingStrategy());

        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));

        Form form = formRepository
                .findByGathering_IdAndDeletedAtIsNull(gatheringId)
                .orElseGet(() -> formRepository.save(
                        Form.builder()
                                .gathering(gathering)
                                .isTemplate(false)
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

        // 시스템 예약 질문(이름/연락처)은 question_key를 바꿀 수 없다 (라벨/순서 등은 허용)
        if (question.isSystemReserved() && !question.getQuestionKey().equals(request.getQuestionKey())) {
            throw new CustomException(ErrorCode.RESERVED_QUESTION_READONLY);
        }

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

        // 시스템 예약 질문(이름/연락처)은 삭제할 수 없다
        if (question.isSystemReserved()) {
            throw new CustomException(ErrorCode.RESERVED_QUESTION_READONLY);
        }
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
