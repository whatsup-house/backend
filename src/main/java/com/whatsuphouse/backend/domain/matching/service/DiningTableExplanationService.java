package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.form.enums.SystemQuestionKey;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableExplanationResponse;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.entity.ScoreDetail;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.enums.Job;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 매칭 근거(설계 6, KAN-432). 저장하지 않고 조회 때 현재 멤버로 엔진을 다시 돌려 계산한다(이동·분리·병합 뒤에도 맞다).
 * 관리자에게는 쌍별 점수·응답 원문까지, 참가자·채팅에는 장점 문구만 준다(MAT-12).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DiningTableExplanationService {

    private static final Set<String> SYSTEM_QUESTION_KEYS = Arrays.stream(SystemQuestionKey.values())
            .map(SystemQuestionKey::getKey)
            .collect(Collectors.toSet());

    private final DiningTableRepository diningTableRepository;
    private final DiningTableMemberRepository diningTableMemberRepository;
    private final DiningMatchService diningMatchService;
    private final MatchingEngine matchingEngine;
    private final AdminApplicationService adminApplicationService;
    private final UserService userService;

    /** 계산 결과. profiles = 신청 ID → 매칭 프로필, strengths = 장점 문구(1개 이상). */
    public record Insight(Map<UUID, MatchingEngine.Applicant> profiles, MatchingEngine.Explanation explanation,
                          List<String> strengths) {
    }

    /** applications(좌석 순) 구성의 근거. 테이블 자신에 함께 앉은 이력은 이전 만남에서 뺀다. */
    public Insight explain(DiningTable table, List<Application> applications) {
        DiningMatchService.TableEvaluator evaluator = diningMatchService.evaluator(table.getSession(), applications, table.getId());
        List<MatchingEngine.Applicant> members = applications.stream()
                .map(application -> evaluator.profiles().get(application.getId()))
                .toList();
        MatchingEngine.Explanation explanation = matchingEngine.explain(members, evaluator.rules(), evaluator.relations());
        return new Insight(evaluator.profiles(), explanation,
                DiningTableStrengths.describe(explanation, members, evaluator.relations()));
    }

    /** 관리자 매칭 근거. 해체 여부와 상관없이 soft delete되지 않은 테이블이면 보여준다. 없으면 404. */
    public DiningTableExplanationResponse getExplanation(UUID tableId) {
        DiningTable table = diningTableRepository.findById(tableId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
        List<Application> applications = diningTableMemberRepository.findByTableIdWithApplication(tableId).stream()
                .map(DiningTableMember::getApplication)
                .filter(application -> application.getDeletedAt() == null)
                .toList();
        Insight insight = explain(table, applications);
        Map<UUID, List<ApplicationAnswer>> answers = adminApplicationService
                .findAnswers(applications.stream().map(Application::getId).toList()).stream()
                .collect(Collectors.groupingBy(answer -> answer.getApplication().getId()));
        ScoreDetail detail = insight.explanation().group().detail();

        return DiningTableExplanationResponse.builder()
                .tableId(table.getId())
                .region(table.getDisplayRegion())
                .groupScore(insight.explanation().group().value())
                .scoreDetail(new ScoreDetail(detail.pairAvg(), detail.pairMin(), detail.penalties().entrySet().stream()
                        .filter(penalty -> penalty.getValue() > 0)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .criteriaAvg(insight.explanation().criteriaAvg())
                .strengths(insight.strengths())
                .members(applications.stream()
                        .map(application -> toMemberView(application, insight.profiles().get(application.getId()),
                                toAnswerViews(application, answers.getOrDefault(application.getId(), List.of()))))
                        .toList())
                .pairs(insight.explanation().pairs().stream()
                        .map(pair -> DiningTableExplanationResponse.PairView.builder()
                                .a(pair.a())
                                .b(pair.b())
                                .score(pair.score())
                                .components(pair.components())
                                .build())
                        .toList())
                .build();
    }

    private DiningTableExplanationResponse.MemberView toMemberView(Application application, MatchingEngine.Applicant profile,
                                                                   List<DiningTableExplanationResponse.AnswerView> answers) {
        User user = application.getUser();
        String jobCode = user != null ? user.getJob() : null;
        return DiningTableExplanationResponse.MemberView.builder()
                .applicationId(application.getId())
                .userId(user != null ? user.getId() : null)
                .nickname(user != null ? user.getNickname() : application.getName())
                .characterUrl(userService.resolveCharacterUrl(jobCode))
                .job(Job.fromCode(jobCode).map(Job::getLabel).orElse(null))
                .birthYear(profile.birthYear())
                .gender(profile.gender())
                .mbti(profile.mbti())
                .answers(answers)
                .build();
    }

    // 응답 원문. 라벨은 신청 당시 formSnapshot 값(없으면 현재 질문 라벨), 질문 순. 시스템 질문(이름·연락처·이메일)과 빈 답은 뺀다.
    private static List<DiningTableExplanationResponse.AnswerView> toAnswerViews(Application application,
                                                                              List<ApplicationAnswer> answers) {
        Map<String, String> labels = snapshotLabels(application);
        return answers.stream()
                .filter(answer -> !SYSTEM_QUESTION_KEYS.contains(answer.getQuestion().getQuestionKey()))
                .filter(answer -> answerText(answer) != null)
                .sorted(Comparator.comparingInt(answer -> answer.getQuestion().getDisplayOrder()))
                .map(answer -> DiningTableExplanationResponse.AnswerView.builder()
                        .label(labels.getOrDefault(answer.getQuestion().getId().toString(), answer.getQuestion().getLabel()))
                        .value(answerText(answer))
                        .build())
                .toList();
    }

    // formSnapshot {"questions": [{questionId, label, ...}]} → questionId → label
    private static Map<String, String> snapshotLabels(Application application) {
        Object questions = application.getFormSnapshot() != null ? application.getFormSnapshot().get("questions") : null;
        if (!(questions instanceof Collection<?> list)) {
            return Map.of();
        }
        Map<String, String> labels = new HashMap<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> question && question.get("questionId") != null && question.get("label") != null) {
                labels.put(question.get("questionId").toString(), question.get("label").toString());
            }
        }
        return labels;
    }

    // 답 값(단일 값 또는 목록)을 표시 문자열로. 비어 있으면 null.
    private static String answerText(ApplicationAnswer answer) {
        Object value = answer.getValue() != null ? answer.getValue().get("value") : null;
        String text = value instanceof Collection<?> collection
                ? collection.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.joining(", "))
                : Objects.toString(value, "");
        return text.isBlank() ? null : text;
    }
}
