package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.application.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.FormRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchingResultResponse;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public MatchingRunResponse runMatching(UUID gatheringId, int groupSize) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        if (gathering.getGatheringType() != GatheringType.RANDOM_TABLE) {
            throw new CustomException(ErrorCode.MATCHING_NOT_ALLOWED);
        }
        // 그룹 인원 수는 2~8 범위로 보정, 벗어나면 기본값 사용. (KAN-224)
        int size = (groupSize >= 2 && groupSize <= 8) ? groupSize : MatchingEngine.DEFAULT_GROUP_SIZE;

        // 1. 기존 추천(PENDING) 결과 정리 — 확정(CONFIRMED) 그룹은 유지한다.
        clearPendingGroups(gatheringId);

        // 2. 이미 살아있는 그룹(=확정 그룹)에 배정된 신청은 재매칭 대상에서 제외한다.
        //    (확정 그룹 멤버를 다시 매칭하면 application_id 유니크 제약을 위반한다.)
        Set<UUID> assignedAppIds = loadAssignedApplicationIds(gatheringId);

        // 3. CONFIRMED 신청 중 아직 배정되지 않은 신청만 매칭 대상으로 삼는다.
        List<Application> applications = applicationRepository
                .findByGatheringIdAndStatusAndDeletedAtIsNull(gatheringId, ApplicationStatus.CONFIRMED).stream()
                .filter(a -> !assignedAppIds.contains(a.getId()))
                .toList();
        List<UUID> appIds = applications.stream().map(Application::getId).toList();

        // 4. 매칭 설정 (form_questions where is_matching_field)
        List<MatchingEngine.MatchingField> fields = loadMatchingFields(gatheringId);

        // 5. 신청별 답변 맵 (question_key → 값)
        Map<UUID, Map<String, Object>> answersByApp = loadAnswers(appIds);

        // 6. Applicant 구성
        List<MatchingEngine.Applicant> applicants = applications.stream()
                .map(a -> new MatchingEngine.Applicant(
                        a.getId(), answersByApp.getOrDefault(a.getId(), Map.of())))
                .toList();

        // 7. 엔진 실행
        List<MatchingEngine.GroupResult> groups =
                matchingEngine.match(applicants, fields, gathering.getEventDate(), size);

        // 8. 저장
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

    // 현재 게더링의 살아있는 그룹(PENDING 정리 후 남은 = 확정 그룹)에 배정된 신청 ID 집합
    private Set<UUID> loadAssignedApplicationIds(UUID gatheringId) {
        List<MatchingGroup> groups = matchingGroupRepository
                .findByGathering_IdAndDeletedAtIsNullOrderByEventDateAsc(gatheringId);
        if (groups.isEmpty()) return Set.of();
        List<UUID> groupIds = groups.stream().map(MatchingGroup::getId).toList();
        return matchingMemberRepository.findByGroupIdsWithApplication(groupIds).stream()
                .map(m -> m.getApplication().getId())
                .collect(Collectors.toSet());
    }

    private void clearPendingGroups(UUID gatheringId) {
        List<MatchingGroup> pending = matchingGroupRepository
                .findByGathering_IdAndStatusAndDeletedAtIsNull(gatheringId, MatchingGroupStatus.PENDING);
        if (pending.isEmpty()) return;
        matchingMemberRepository.deleteByGroupIn(pending);
        matchingGroupRepository.deleteAll(pending);
        // 기존 멤버 DELETE를 즉시 DB에 반영한다. flush하지 않으면 Hibernate 기본 flush 순서상
        // 새 멤버 INSERT가 기존 멤버 DELETE보다 먼저 실행되어 application_id 유니크 제약을 위반한다.
        matchingMemberRepository.flush();
        matchingGroupRepository.flush();
    }

    // ── 관리자 검토 ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MatchingResultResponse getMatchingResult(UUID gatheringId) {
        gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));

        List<MatchingGroup> groups = matchingGroupRepository
                .findByGathering_IdAndDeletedAtIsNullOrderByEventDateAsc(gatheringId);
        List<UUID> groupIds = groups.stream().map(MatchingGroup::getId).toList();
        List<MatchingMember> members = groupIds.isEmpty()
                ? List.of()
                : matchingMemberRepository.findByGroupIdsWithApplication(groupIds);

        Map<UUID, List<MatchingMember>> byGroup = members.stream()
                .collect(java.util.stream.Collectors.groupingBy(m -> m.getGroup().getId()));
        java.util.Set<UUID> matchedAppIds = members.stream()
                .map(m -> m.getApplication().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<MatchingResultResponse.GroupView> groupViews = groups.stream()
                .map(g -> MatchingResultResponse.GroupView.builder()
                        .groupId(g.getId())
                        .eventDate(g.getEventDate())
                        .status(g.getStatus())
                        .groupScore(g.getGroupScore())
                        .groupSize(g.getGroupSize())
                        .restaurantName(g.getRestaurantName())
                        .restaurantAddress(g.getRestaurantAddress())
                        .members(byGroup.getOrDefault(g.getId(), List.of()).stream()
                                .map(this::toMemberView).toList())
                        .build())
                .toList();

        List<MatchingResultResponse.MemberView> unmatched = applicationRepository
                .findByGatheringIdAndStatusAndDeletedAtIsNull(gatheringId, ApplicationStatus.CONFIRMED).stream()
                .filter(a -> !matchedAppIds.contains(a.getId()))
                .map(a -> MatchingResultResponse.MemberView.builder()
                        .applicationId(a.getId()).name(a.getName()).phone(a.getPhone()).build())
                .toList();

        return MatchingResultResponse.builder()
                .gatheringId(gatheringId).groups(groupViews).unmatched(unmatched).build();
    }

    private MatchingResultResponse.MemberView toMemberView(MatchingMember m) {
        return MatchingResultResponse.MemberView.builder()
                .memberId(m.getId())
                .applicationId(m.getApplication().getId())
                .name(m.getApplication().getName())
                .phone(m.getApplication().getPhone())
                .seatOrder(m.getSeatOrder())
                .manualAssign(m.isManualAssign())
                .build();
    }

    @Transactional
    public void moveMember(UUID memberId, UUID targetGroupId) {
        MatchingMember member = matchingMemberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_MEMBER_NOT_FOUND));
        MatchingGroup oldGroup = member.getGroup();
        MatchingGroup target = matchingGroupRepository.findById(targetGroupId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));

        member.moveTo(target, matchingMemberRepository.countByGroup_Id(targetGroupId) + 1);
        matchingMemberRepository.flush();
        oldGroup.updateGroupSize(matchingMemberRepository.countByGroup_Id(oldGroup.getId()));
        target.updateGroupSize(matchingMemberRepository.countByGroup_Id(targetGroupId));
        recalculateGroupScore(oldGroup);
        recalculateGroupScore(target);
    }

    @Transactional
    public void excludeMember(UUID memberId) {
        MatchingMember member = matchingMemberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_MEMBER_NOT_FOUND));
        MatchingGroup group = member.getGroup();
        matchingMemberRepository.delete(member);
        matchingMemberRepository.flush();
        group.updateGroupSize(matchingMemberRepository.countByGroup_Id(group.getId()));
        recalculateGroupScore(group);
    }

    @Transactional
    public void assignMember(UUID groupId, UUID applicationId) {
        MatchingGroup group = matchingGroupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
        if (matchingMemberRepository.existsByApplication_Id(applicationId)) {
            throw new CustomException(ErrorCode.MATCHING_ALREADY_ASSIGNED);
        }
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        matchingMemberRepository.save(MatchingMember.builder()
                .application(application)
                .group(group)
                .seatOrder(matchingMemberRepository.countByGroup_Id(groupId) + 1)
                .isManualAssign(true)
                .build());
        matchingMemberRepository.flush();
        group.updateGroupSize(matchingMemberRepository.countByGroup_Id(groupId));
        recalculateGroupScore(group);
    }

    private void recalculateGroupScore(MatchingGroup group) {
        List<MatchingMember> members = matchingMemberRepository.findByGroupIdWithApplication(group.getId());
        if (members.isEmpty()) {
            group.updateGroupScore(BigDecimal.ZERO);
            return;
        }

        List<UUID> applicationIds = members.stream()
                .map(member -> member.getApplication().getId())
                .toList();
        Map<UUID, Map<String, Object>> answersByApp = loadAnswers(applicationIds);
        List<MatchingEngine.Applicant> applicants = members.stream()
                .map(member -> new MatchingEngine.Applicant(
                        member.getApplication().getId(),
                        answersByApp.getOrDefault(member.getApplication().getId(), Map.of())))
                .toList();

        group.updateGroupScore(matchingEngine.scoreGroup(
                applicants,
                loadMatchingFields(group.getGathering().getId())));
    }

    @Transactional
    public void confirmGroup(UUID groupId) {
        MatchingGroup group = matchingGroupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
        group.confirm();
    }

    @Transactional
    public void updateRestaurant(UUID groupId, String name, String address) {
        MatchingGroup group = matchingGroupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
        group.updateRestaurant(name, address);
    }
}
