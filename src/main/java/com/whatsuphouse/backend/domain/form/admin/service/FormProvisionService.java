package com.whatsuphouse.backend.domain.form.admin.service;

import com.whatsuphouse.backend.domain.form.enums.SystemQuestionKey;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 게더링 생성 시 신청폼을 만들고, 시스템 예약 질문(이름/연락처/이메일)을 자동으로 시드한다.
 * 이름/연락처/이메일은 applications.name/phone/email 컬럼으로 전달되는 필수 정보라 관리자가 삭제/키변경할 수 없다.
 * 이메일은 비회원 신청 알림 발송에 사용된다(회원은 계정 이메일 사용).
 * 나머지 질문은 관리자가 자유롭게 추가/삭제한다.
 * 우연한 식탁(RANDOM_TABLE) 종류 폼에는 템플릿 '우연한 식탁 표준 폼'의 표준 질문 7개를 복사한다. (KAN-341)
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

        List<FormQuestion> questions = new ArrayList<>(List.of(
                reservedQuestion(form, SystemQuestionKey.NAME.getKey(), "이름", 0),
                reservedQuestion(form, SystemQuestionKey.PHONE.getKey(), "연락처", 1),
                reservedQuestion(form, SystemQuestionKey.EMAIL.getKey(), "이메일", 2)
        ));
        if (gathering.getGatheringType() == GatheringType.RANDOM_TABLE) {
            List<FormQuestion> standard = formQuestionRepository.findTemplateReservedQuestions(GatheringType.RANDOM_TABLE);
            validateStandardQuestions(standard);
            for (FormQuestion q : standard) {
                questions.add(q.copyTo(form, questions.size()));
            }
        }
        formQuestionRepository.saveAll(questions);

        return form;
    }

    /** 신청용: 종류 폼을 찾고, 없으면(시드/레거시) 기본 폼을 만든다. (KAN-206, KAN-393) */
    @Transactional
    public Form findOrCreateForm(Gathering gathering) {
        return formRepository.findByGathering_IdAndDeletedAtIsNull(gathering.getId())
                .orElseGet(() -> createDefaultForm(gathering));
    }

    /** 폼의 질문(표시 순). (KAN-393) */
    public List<FormQuestion> findQuestions(Form form) {
        return formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(form);
    }

    // RANDOM_TABLE 종류에 연결되는 폼은 표준 질문 7개를 모두 가져야 한다. 템플릿이 빠져 있으면 폼 생성(=종류 생성)을 막는다.
    private void validateStandardQuestions(List<FormQuestion> questions) {
        EnumSet<ReservedQuestionKey> present = questions.stream()
                .map(FormQuestion::getReservedKey)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ReservedQuestionKey.class)));
        if (present.size() != ReservedQuestionKey.values().length || questions.size() != present.size()) {
            throw new CustomException(ErrorCode.STANDARD_QUESTIONS_MISSING);
        }
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
