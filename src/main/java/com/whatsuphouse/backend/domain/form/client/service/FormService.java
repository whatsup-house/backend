package com.whatsuphouse.backend.domain.form.client.service;

import com.whatsuphouse.backend.domain.form.admin.service.FormProvisionService;
import com.whatsuphouse.backend.domain.form.client.dto.response.GatheringFormResponse;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.FormRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FormService {

    private final GatheringRepository gatheringRepository;
    private final FormRepository formRepository;
    private final FormQuestionRepository formQuestionRepository;
    private final FormProvisionService formProvisionService;

    // 폼이 없는 게더링(시드/레거시)도 신청 가능하도록 조회 시점에 기본 폼을 프로비저닝한다. (KAN-206)
    @Transactional
    public GatheringFormResponse getForm(UUID gatheringId) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));

        Form form = formRepository
                .findByGathering_IdAndDeletedAtIsNull(gatheringId)
                .orElseGet(() -> formProvisionService.createDefaultForm(gathering));

        List<FormQuestion> questions = formQuestionRepository
                .findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(form);

        return GatheringFormResponse.from(form, questions);
    }
}
