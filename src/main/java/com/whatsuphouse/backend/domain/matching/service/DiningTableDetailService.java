package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.dining.service.SessionVenueService;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableDetailResponse;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.repository.AttendanceRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 참가자 테이블 상세(GET /api/dining/tables/{id}). (KAN-346) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DiningTableDetailService {

    /** 테이블 상세·확정 채팅 안내에 쓰는 취소 정책. 기한은 DiningApplicationService의 사전 취소 기한(2일)과 같다. */
    public static final String CANCEL_POLICY =
            "회차 시작 2일 전까지 취소하면 이용권이 복원돼요. 그 뒤에는 취소할 수 없고, 참석하지 않으면 노쇼로 기록돼요.";

    private static final Set<DiningTableStatus> VISIBLE_STATUSES = EnumSet.of(DiningTableStatus.CONFIRMED, DiningTableStatus.DONE);

    private final DiningTableRepository diningTableRepository;
    private final DiningTableMemberRepository diningTableMemberRepository;
    private final AttendanceRepository attendanceRepository;
    private final DiningMatchService diningMatchService;
    private final SessionVenueService sessionVenueService;

    /** 본인이 멤버(취소하지 않은 신청)인 확정·완료 테이블만. 없으면 404, 그 외는 403. */
    public DiningTableDetailResponse getTable(UUID tableId, UUID userId) {
        DiningTable table = diningTableRepository.findById(tableId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
        List<DiningTableMember> members = diningTableMemberRepository.findByTableIdWithApplication(tableId).stream()
                .filter(member -> member.getApplication().getDeletedAt() == null)
                .toList();
        DiningTableMember me = members.stream()
                .filter(member -> member.getApplication().getUser() != null
                        && member.getApplication().getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);
        if (me == null || !VISIBLE_STATUSES.contains(table.getStatus())) {
            throw new CustomException(ErrorCode.DINING_TABLE_FORBIDDEN);
        }

        Map<UUID, MatchingEngine.Applicant> profiles = diningMatchService.findProfiles(
                members.stream().map(DiningTableMember::getApplication).toList());
        GatheringSession session = table.getSession();
        return DiningTableDetailResponse.builder()
                .id(table.getId())
                .status(table.getStatus())
                .session(DiningTableDetailResponse.SessionView.builder()
                        .eventDate(session.getEventDate())
                        .startTime(session.getStartTime())
                        .endTime(session.getEndTime())
                        .region(table.getDisplayRegion())
                        .build())
                .venue(table.getVenueId() == null ? null : sessionVenueService.findVenue(table.getVenueId())
                        .map(DiningTableDetailResponse.VenueView::from).orElse(null))
                .chatRoomId(table.getChatRoomId())
                .members(members.stream().map(member -> toMemberView(member, profiles)).toList())
                .cancelPolicy(CANCEL_POLICY)
                .myAttendance(attendanceRepository.findByTableMemberId(me.getId())
                        .map(DiningTableDetailResponse.AttendanceView::from).orElse(null))
                .build();
    }

    private static DiningTableDetailResponse.MemberView toMemberView(DiningTableMember member,
                                                                     Map<UUID, MatchingEngine.Applicant> profiles) {
        Application application = member.getApplication();
        User user = application.getUser();
        MatchingEngine.Applicant profile = profiles.get(application.getId());
        return DiningTableDetailResponse.MemberView.builder()
                .userId(user != null ? user.getId() : null)
                .nickname(user != null ? user.getNickname() : null)
                .mbti(profile != null ? profile.mbti() : null)
                .interests(profile != null ? List.copyOf(profile.interests()) : List.of())
                .build();
    }
}
