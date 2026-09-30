package com.whatsuphouse.backend.domain.form.admin.service;

import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.FormRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class FormProvisionServiceTest {

    @Mock
    private FormRepository formRepository;

    @Mock
    private FormQuestionRepository formQuestionRepository;

    @InjectMocks
    private FormProvisionService formProvisionService;

    private final Gathering randomTable = Gathering.builder()
            .title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();

    private List<FormQuestion> template(ReservedQuestionKey... keys) {
        Form templateForm = Form.builder().isTemplate(true).gatheringType(GatheringType.RANDOM_TABLE).build();
        return Arrays.stream(keys)
                .map(key -> FormQuestion.builder()
                        .form(templateForm)
                        .questionKey(key.name().toLowerCase())
                        .type(QuestionType.SHORT_TEXT)
                        .label(key.name())
                        .required(true)
                        .displayOrder(key.ordinal())
                        .reservedKey(key)
                        .build())
                .toList();
    }

    @Test
    @DisplayName("우연한 식탁 종류 폼에는 예약 질문 3개 뒤에 표준 질문 7개가 복사된다")
    @SuppressWarnings("unchecked")
    void createDefaultForm_randomTable_copiesStandardQuestions() {
        given(formRepository.save(any(Form.class))).willAnswer(inv -> inv.getArgument(0));
        given(formQuestionRepository.findTemplateReservedQuestions(GatheringType.RANDOM_TABLE))
                .willReturn(template(ReservedQuestionKey.values()));

        Form form = formProvisionService.createDefaultForm(randomTable);

        ArgumentCaptor<List<FormQuestion>> saved = ArgumentCaptor.forClass(List.class);
        then(formQuestionRepository).should().saveAll(saved.capture());
        List<FormQuestion> questions = saved.getValue();
        assertThat(questions).hasSize(10);
        assertThat(questions).allMatch(q -> q.getForm() == form);
        assertThat(questions.subList(3, 10)).extracting(FormQuestion::getReservedKey)
                .containsExactly(ReservedQuestionKey.values());
        assertThat(questions).extracting(FormQuestion::getDisplayOrder)
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    @DisplayName("템플릿에 표준 질문이 빠져 있으면 우연한 식탁 종류에 폼을 연결할 수 없다")
    void createDefaultForm_randomTable_missingStandardQuestion_throws() {
        given(formRepository.save(any(Form.class))).willAnswer(inv -> inv.getArgument(0));
        given(formQuestionRepository.findTemplateReservedQuestions(GatheringType.RANDOM_TABLE))
                .willReturn(template(ReservedQuestionKey.BIRTH_YEAR, ReservedQuestionKey.GENDER));

        assertThatThrownBy(() -> formProvisionService.createDefaultForm(randomTable))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STANDARD_QUESTIONS_MISSING);
        then(formQuestionRepository).should(never()).saveAll(any());
    }
}
