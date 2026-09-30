package com.whatsuphouse.backend.domain.form.client.service;

import com.whatsuphouse.backend.domain.form.admin.service.FormProvisionService;
import com.whatsuphouse.backend.domain.form.client.dto.response.GatheringFormResponse;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.FormRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FormService {

    private final GatheringService gatheringService;
    private final FormRepository formRepository;
    private final FormQuestionRepository formQuestionRepository;
    private final FormProvisionService formProvisionService;

    // 폼이 없는 게더링(시드/레거시)도 신청 가능하도록 조회 시점에 기본 폼을 프로비저닝한다. (KAN-206)
    // 폼은 종류 단위다. 경로 ID가 회차 ID면 그 회차의 종류 폼을 준다. (KAN-337)
    @Transactional
    public GatheringFormResponse getForm(UUID gatheringId) {
        Gathering gathering = gatheringService.findGathering(gatheringId);

        Form form = formRepository
                .findByGathering_IdAndDeletedAtIsNull(gathering.getId())
                .orElseGet(() -> formProvisionService.createDefaultForm(gathering));

        List<FormQuestion> questions = formQuestionRepository
                .findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(form);

        return GatheringFormResponse.from(form, questions);
    }

    /**
     * 종류 폼의 표준 질문 키(reservedKey → questionKey). 폼이 아직 없으면 생성 시 복사될 템플릿 표준 질문을 쓴다.
     * 프리필(KAN-342)용 조회라 폼을 새로 만들지 않는다.
     */
    public Map<ReservedQuestionKey, String> findReservedQuestionKeys(UUID gatheringId) {
        Gathering gathering = gatheringService.findGathering(gatheringId);
        List<FormQuestion> questions = formRepository.findByGathering_IdAndDeletedAtIsNull(gathering.getId())
                .map(formQuestionRepository::findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc)
                .orElseGet(() -> formQuestionRepository.findTemplateReservedQuestions(gathering.getGatheringType()));
        Map<ReservedQuestionKey, String> keys = new EnumMap<>(ReservedQuestionKey.class);
        questions.stream()
                .filter(q -> q.getReservedKey() != null)
                .forEach(q -> keys.put(q.getReservedKey(), q.getQuestionKey()));
        return keys;
    }
}
