package com.whatsuphouse.backend.domain.application.client.dto.response;

import com.whatsuphouse.backend.domain.form.entity.ApplicationAnswer;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class AnswerView {

    private String questionKey;
    private String label;
    private Object value;

    public static AnswerView from(ApplicationAnswer answer) {
        Map<String, Object> raw = answer.getValue();
        return AnswerView.builder()
                .questionKey(answer.getQuestion().getQuestionKey())
                .label(answer.getQuestion().getLabel())
                // 저장 형태가 {"value": ...} 이므로 내부 값을 펼쳐서 노출
                .value(raw != null ? raw.get("value") : null)
                .build();
    }
}
