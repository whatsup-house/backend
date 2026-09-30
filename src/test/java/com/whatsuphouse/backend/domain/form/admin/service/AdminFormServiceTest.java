package com.whatsuphouse.backend.domain.form.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsuphouse.backend.domain.form.admin.dto.request.FormQuestionUpdateRequest;
import com.whatsuphouse.backend.domain.form.admin.dto.response.FormQuestionResponse;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.MatchingStrategy;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

// 우연한 식탁 표준 질문(reserved_key) 잠금 규칙 (KAN-341)
@ExtendWith(MockitoExtension.class)
class AdminFormServiceTest {

    @Mock
    private FormQuestionRepository formQuestionRepository;

    @InjectMocks
    private AdminFormService adminFormService;

    private final UUID questionId = UUID.randomUUID();

    private FormQuestion genderQuestion() {
        return FormQuestion.builder()
                .form(Form.builder().build())
                .questionKey("gender")
                .type(QuestionType.SINGLE_CHOICE)
                .label("성별")
                .required(true)
                .displayOrder(1)
                .options(Map.of("choices", List.of("MALE", "FEMALE")))
                .isMatchingField(true)
                .matchingStrategy(MatchingStrategy.DIVERSE)
                .matchingWeight(BigDecimal.ONE)
                .reservedKey(ReservedQuestionKey.GENDER)
                .build();
    }

    private FormQuestionUpdateRequest request(String questionKey, String type) throws Exception {
        return new ObjectMapper().readValue("""
                {"questionKey":"%s","type":"%s","label":"성별을 알려주세요","required":false,"displayOrder":5,
                 "options":{"choices":["MALE","FEMALE","OTHER"]},"isMatchingField":false}
                """.formatted(questionKey, type), FormQuestionUpdateRequest.class);
    }

    @Test
    @DisplayName("표준 질문은 삭제할 수 없다")
    void deleteQuestion_reserved_throws() {
        given(formQuestionRepository.findById(questionId)).willReturn(Optional.of(genderQuestion()));

        assertThatThrownBy(() -> adminFormService.deleteQuestion(questionId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVED_QUESTION_LOCKED);
    }

    @Test
    @DisplayName("표준 질문의 타입은 바꿀 수 없다")
    void updateQuestion_reservedTypeChange_throws() throws Exception {
        given(formQuestionRepository.findById(questionId)).willReturn(Optional.of(genderQuestion()));
        FormQuestionUpdateRequest request = request("gender", "MULTI_CHOICE");

        assertThatThrownBy(() -> adminFormService.updateQuestion(questionId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVED_QUESTION_LOCKED);
    }

    @Test
    @DisplayName("표준 질문은 라벨·선택지·순서만 바뀌고 필수 여부·매칭 설정은 유지된다")
    void updateQuestion_reserved_updatesLabelAndOptionsOnly() throws Exception {
        given(formQuestionRepository.findById(questionId)).willReturn(Optional.of(genderQuestion()));

        FormQuestionResponse response = adminFormService.updateQuestion(questionId, request("gender", "SINGLE_CHOICE"));

        assertThat(response.getLabel()).isEqualTo("성별을 알려주세요");
        assertThat(response.getOptions()).isEqualTo(Map.of("choices", List.of("MALE", "FEMALE", "OTHER")));
        assertThat(response.getDisplayOrder()).isEqualTo(5);
        assertThat(response.isRequired()).isTrue();
        assertThat(response.isMatchingField()).isTrue();
        assertThat(response.getMatchingStrategy()).isEqualTo(MatchingStrategy.DIVERSE);
        assertThat(response.getReservedKey()).isEqualTo(ReservedQuestionKey.GENDER);
    }
}
