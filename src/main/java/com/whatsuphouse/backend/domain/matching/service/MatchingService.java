package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.form.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.FormRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchingRunResponse;
import com.whatsuphouse.backend.domain.matching.entity.MatchingGroup;
import com.whatsuphouse.backend.domain.matching.entity.MatchingMember;
import com.whatsuphouse.backend.domain.matching.enums.MatchingGroupStatus;
import com.whatsuphouse.backend.domain.matching.repository.MatchingGroupRepository;
import com.whatsuphouse.backend.domain.matching.repository.MatchingMemberRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchingService {

    private static final String ALGORITHM_VERSION = "rule-v1";

    private final GatheringRepository gatheringRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationAnswerRepository applicationAnswerRepository;
    private final FormRepository formRepository;
    private final FormQuestionRepository formQuestionRepository;
    private final MatchingGroupRepository matchingGroupRepository;
    private final MatchingMemberRepository matchingMemberRepository;
    private final MatchingEngine matchingEngine;

    @Transactional
    public MatchingRunResponse runMatching(UUID gatheringId) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        if (gathering.getGatheringType() != GatheringType.RANDOM_TABLE) {
            throw new CustomException(ErrorCode.MATCHING_NOT_ALLOWED);
        }

        // 1. CONFIRMED 신청 대상
        List<Application> applications = applicationRepository
                .findByGatheringIdAndStatusAndDeletedAtIsNull(gatheringId, ApplicationStatus.CONFIRMED);
        List<UUID> appIds = applications.stream().map(Application::getId).toList();

        // 2. 매칭 설정 (form_questions where is_matching_field)
        List<MatchingEngine.MatchingField> fields = loadMatchingFields(gatheringId);

        // 3. 신청별 답변 맵 (question_key → 값)
        Map<UUID, Map<String, Object>> answersByApp = loadAnswers(appIds);

        // 4. Applicant 구성
        List<MatchingEngine.Applicant> applicants = applications.stream()
                .map(a -> new MatchingEngine.Applicant(
                        a.getId(), answersByApp.getOrDefault(a.getId(), Map.of())))
                .toList();

        // 5. 기존 추천(PENDING) 결과 정리 후 재실행
        clearPendingGroups(gatheringId);

        // 6. 엔진 실행
        List<MatchingEngine.GroupResult> groups =
                matchingEngine.match(applicants, fields, gathering.getEventDate());

        // 7. 저장
        Map<UUID, Application> appMap = new HashMap<>();
        applications.forEach(a -> appMap.put(a.getId(), a));
        int matched = 0;
        for (MatchingEngine.GroupResult g : groups) {
            MatchingGroup group = matchingGroupRepository.save(MatchingGroup.builder()
                    .gathering(gathering)
                    .eventDate(g.eventDate())
                    .groupSize(g.applicationIds().size())
                    .algorithmVersion(ALGORITHM_VERSION)
                    .groupScore(g.score())
                    .build());
            group.markMatched(LocalDateTime.now());

            int seat = 1;
            for (UUID appId : g.applicationIds()) {
                matchingMemberRepository.save(MatchingMember.builder()
                        .application(appMap.get(appId))
                        .group(group)
                        .seatOrder(seat++)
                        .isManualAssign(false)
                        .build());
                matched++;
            }
        }

        return MatchingRunResponse.builder()
                .gatheringId(gatheringId)
                .algorithmVersion(ALGORITHM_VERSION)
                .confirmedCount(applications.size())
                .groupCount(groups.size())
                .matchedCount(matched)
                .unmatchedCount(applications.size() - matched)
                .build();
    }

    private List<MatchingEngine.MatchingField> loadMatchingFields(UUID gatheringId) {
        Form form = formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)
                .orElseThrow(() -> new CustomException(ErrorCode.FORM_NOT_FOUND));
        return formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(form).stream()
                .filter(FormQuestion::isMatchingField)
                .filter(q -> q.getMatchingStrategy() != null)
                .map(q -> new MatchingEngine.MatchingField(
                        q.getQuestionKey(),
                        q.getMatchingStrategy(),
                        q.getMatchingWeight() != null ? q.getMatchingWeight().doubleValue() : 1.0))
                .toList();
    }

    private Map<UUID, Map<String, Object>> loadAnswers(List<UUID> appIds) {
        Map<UUID, Map<String, Object>> result = new HashMap<>();
        if (appIds.isEmpty()) return result;
        for (ApplicationAnswer aa : applicationAnswerRepository.findByApplicationIds(appIds)) {
            UUID appId = aa.getApplication().getId();
            Object value = aa.getValue() != null ? aa.getValue().get("value") : null;
            result.computeIfAbsent(appId, k -> new HashMap<>())
                    .put(aa.getQuestion().getQuestionKey(), value);
        }
        return result;
    }

    private void clearPendingGroups(UUID gatheringId) {
        List<MatchingGroup> pending = matchingGroupRepository
                .findByGathering_IdAndStatusAndDeletedAtIsNull(gatheringId, MatchingGroupStatus.PENDING);
        if (pending.isEmpty()) return;
        matchingMemberRepository.deleteByGroupIn(pending);
        matchingGroupRepository.deleteAll(pending);
    }
}
