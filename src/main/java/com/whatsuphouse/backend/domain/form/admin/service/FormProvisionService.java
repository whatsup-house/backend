package com.whatsuphouse.backend.domain.form.admin.service;

import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.FormRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 게더링 생성 시 신청폼을 만들고, 시스템 예약 질문(이름/연락처)을 자동으로 시드한다.
 * 이름/연락처는 applications.name/phone 컬럼으로 전달되는 필수 정보라 관리자가 삭제/키변경할 수 없다.
 * 나머지 질문은 관리자가 자유롭게 추가/삭제한다.
 */
@Service
@RequiredArgsConstructor
public class FormProvisionService {

    private final FormRepository formRepository;
    private final FormQuestionRepository formQuestionRepository;

    @Transactional
    public Form createDefaultForm(Gathering gathering) {
        Form form = formRepository.save(
                Form.builder()
                        .gathering(gathering)
                        .isTemplate(false)
                        .build());

        formQuestionRepository.saveAll(List.of(
                reservedQuestion(form, "name", "이름", 0),
                reservedQuestion(form, "phone", "연락처", 1)
        ));

        return form;
    }

    private FormQuestion reservedQuestion(Form form, String questionKey, String label, int order) {
        return FormQuestion.builder()
                .form(form)
                .questionKey(questionKey)
                .type(QuestionType.SHORT_TEXT)
                .label(label)
                .required(true)
                .displayOrder(order)
                .isMatchingField(false)
                .isSystemReserved(true)
                .build();
    }
}
