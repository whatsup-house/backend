package com.whatsuphouse.backend.domain.application.client.dto.response;

import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 우연한 식탁 신청 폼 프리필. 표준 질문별 내 가장 최근 답변. GET /api/dining/prefill (KAN-342, ACC-06) */
@Getter
@AllArgsConstructor
public class DiningPrefillResponse {

    @Schema(description = "표준 질문별 최근 답변. 답한 적 없는 표준 질문은 빠진다")
    private List<Answer> answers;

    @Getter
    @AllArgsConstructor
    public static class Answer {
        @Schema(description = "표준 질문 키", example = "MBTI")
        private ReservedQuestionKey reservedKey;

        @Schema(description = "이 모임 폼에서 해당 표준 질문의 질문 키", example = "mbti")
        private String questionKey;

        @Schema(description = "답변 값 (질문 타입에 따라 문자열·숫자·배열)", example = "ENFP")
        private Object value;
    }
}
