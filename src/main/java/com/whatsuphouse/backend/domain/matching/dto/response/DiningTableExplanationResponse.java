package com.whatsuphouse.backend.domain.matching.dto.response;

import com.whatsuphouse.backend.domain.matching.entity.ScoreDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 관리자 매칭 근거. 저장값이 아니라 조회 시점의 현재 멤버로 엔진이 다시 계산한 값이다. (설계 6.3, KAN-432) */
@Getter
@Builder
public class DiningTableExplanationResponse {

    @Schema(description = "테이블 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID tableId;
    @Schema(description = "테이블 지역. 없으면 null", example = "강남/신논현")
    private String region;
    @Schema(description = "그룹 점수(0.7·pairAvg + 0.3·pairMin − 페널티)", example = "0.72")
    private BigDecimal groupScore;
    @Schema(description = "점수 내역 {pairAvg, pairMin, penalties}. penalties는 0보다 큰 항목만")
    private ScoreDetail scoreDetail;
    @Schema(description = "항목별 쌍 평균(값 있는 항목만). 키: gender, mbti, interests, wantedStyle, custom",
            example = "{\"wantedStyle\": 0.8, \"mbti\": 0.6}")
    private Map<String, Double> criteriaAvg;
    @Schema(description = "장점 문구 1~2개", example = "[\"대화 스타일 80% 일치하는 테이블이에요!\", \"MBTI 궁합 60% 테이블이에요!\"]")
    private List<String> strengths;
    @Schema(description = "멤버, 좌석 순")
    private List<MemberView> members;
    @Schema(description = "모든 멤버 쌍의 점수와 항목 값")
    private List<PairView> pairs;

    @Getter
    @Builder
    public static class MemberView {
        @Schema(description = "신청 ID(pairs의 a·b)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID applicationId;
        @Schema(description = "회원 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID userId;
        @Schema(description = "닉네임", example = "와썹러")
        private String nickname;
        @Schema(description = "직업 캐릭터 이미지 URL", example = "https://example.supabase.co/storage/v1/object/public/assets/characters/IT_DEV.png")
        private String characterUrl;
        @Schema(description = "직업 이름. 모르면 null", example = "개발자")
        private String job;
        @Schema(description = "출생연도(신청 답). 모르면 null", example = "1995")
        private Integer birthYear;
        @Schema(description = "성별(신청 답). 모르면 null", example = "FEMALE")
        private String gender;
        @Schema(description = "MBTI(신청 답). 모르면 null", example = "ENFP")
        private String mbti;
        @Schema(description = "신청서 응답 원문(이름·연락처·이메일 제외), 질문 순")
        private List<AnswerView> answers;
    }

    @Getter
    @Builder
    public static class AnswerView {
        @Schema(description = "질문 라벨(신청 당시)", example = "관심사")
        private String label;
        @Schema(description = "답. 여러 개면 쉼표로 잇는다", example = "전시, 와인")
        private String value;
    }

    @Getter
    @Builder
    public static class PairView {
        @Schema(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID a;
        @Schema(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID b;
        @Schema(description = "페어 점수", example = "0.83")
        private double score;
        @Schema(description = "항목 값(값 없는 항목은 키 없음)", example = "{\"gender\": 1.0, \"mbti\": 0.5, \"interests\": 0.4, \"wantedStyle\": 1.0}")
        private Map<String, Double> components;
    }
}
