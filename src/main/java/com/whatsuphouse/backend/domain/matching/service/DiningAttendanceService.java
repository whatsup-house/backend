package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningAttendanceResponse;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableDetailResponse;
import com.whatsuphouse.backend.domain.matching.entity.Attendance;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.repository.AttendanceRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.DiningTableLifecycleEvent;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 우연한 식탁 참석. 본인 체크인, 운영자 참석 관리, 신청 취소·좌석 변경 훅, 회차 종료 처리(노쇼 후보·테이블 완료·피드백 요청). (설계 4.9, KAN-349)
 * 참석 행은 확정 테이블의 좌석(멤버 행)에 붙는다: 확정 테이블에 앉으면 SCHEDULED를 만들고, 운영 조정으로 좌석을 떠나면 지운다.
 * 신청 취소만 CANCELED_EARLY로 기록을 남긴다(이용권 복원과 짝).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiningAttendanceService {

    static final int CHECKIN_WINDOW_HOURS = 2;
    static final int CLOSING_DELAY_HOURS = 1;
    // 종료 처리할 회차를 찾는 행사일 범위(오늘부터 며칠 전까지). 서버가 이보다 오래 멈춰 놓친 회차는 운영자가 정리한다.
    private static final int CLOSING_LOOKBACK_DAYS = 3;
    private static final Set<DiningTableStatus> ATTENDANCE_TABLE_STATUSES =
            EnumSet.of(DiningTableStatus.CONFIRMED, DiningTableStatus.DONE);
    private static final Set<AttendanceStatus> CANCELED_STATUSES =
            EnumSet.of(AttendanceStatus.CANCELED_EARLY, AttendanceStatus.CANCELED_LATE);

    private final DiningTableRepository diningTableRepository;
    private final DiningTableMemberRepository diningTableMemberRepository;
    private final AttendanceRepository attendanceRepository;
    private final GatheringService gatheringService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    // ── 체크인 ──────────────────────────────────────────────────────────────

    /**
     * 본인 체크인: 확정·완료 테이블의 멤버(취소하지 않은 신청)만, 회차 시작 ±2시간. 이미 ATTENDED면 창과 무관하게 그대로 돌려준다(멱등).
     * 테이블 없음 404, 멤버 아님 403, 창 밖 400, 예정(SCHEDULED)이 아닌 참석 400.
     */
    @Transactional
    public DiningTableDetailResponse.AttendanceView checkIn(UUID tableId, UUID userId) {
        DiningTable table = diningTableRepository.findById(tableId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
        DiningTableMember me = findActiveMembers(tableId).stream()
                .filter(member -> member.getApplication().getUser() != null
                        && member.getApplication().getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);
        if (me == null || !ATTENDANCE_TABLE_STATUSES.contains(table.getStatus())) {
            throw new CustomException(ErrorCode.DINING_TABLE_FORBIDDEN);
        }
        Attendance attendance = attendanceRepository.findByTableMemberId(me.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.ATTENDANCE_NOT_FOUND));
        if (attendance.getStatus() != AttendanceStatus.ATTENDED) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt = table.getSession().getStartAt();
            if (now.isBefore(startAt.minusHours(CHECKIN_WINDOW_HOURS)) || now.isAfter(startAt.plusHours(CHECKIN_WINDOW_HOURS))) {
                throw new CustomException(ErrorCode.CHECKIN_WINDOW_CLOSED);
            }
            attendance.checkIn(now);
        }
        return DiningTableDetailResponse.AttendanceView.from(attendance);
    }

    // ── 운영자 참석 관리 ─────────────────────────────────────────────────────

    /** 회차 콘솔 참석 탭: 확정·완료 테이블 멤버 중 참석 행이 있는 사람(취소한 신청 포함), 테이블 생성 순 → 좌석 순. */
    @Transactional(readOnly = true)
    public List<DiningAttendanceResponse> listSessionAttendances(UUID sessionId) {
        gatheringService.findRandomTableSession(sessionId);
        List<UUID> tableIds = diningTableRepository
                .findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(sessionId, ATTENDANCE_TABLE_STATUSES).stream()
                .map(DiningTable::getId)
                .toList();
        if (tableIds.isEmpty()) {
            return List.of();
        }
        List<DiningTableMember> members = diningTableMemberRepository.findByTableIdsWithApplication(tableIds);
        Map<UUID, Attendance> byMember = findAttendancesByMember(members);
        return members.stream()
                .filter(member -> byMember.containsKey(member.getId()))
                .sorted(Comparator.comparingInt(member -> tableIds.indexOf(member.getTable().getId())))
                .map(member -> DiningAttendanceResponse.of(byMember.get(member.getId()), member))
                .toList();
    }

    /** 운영자 상태 확정(NO_SHOW·CANCELED_LATE·ATTENDED). 바꾼 관리자를 updated_by에 남긴다. 없으면 404, 허용되지 않는 전이 400. */
    @Transactional
    public DiningAttendanceResponse changeAttendanceStatus(UUID attendanceId, AttendanceStatus status, UUID adminId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new CustomException(ErrorCode.ATTENDANCE_NOT_FOUND));
        attendance.changeStatus(status, adminId);
        DiningTableMember member = diningTableMemberRepository.findById(attendance.getTableMemberId())
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_MEMBER_NOT_FOUND));
        return DiningAttendanceResponse.of(attendance, member);
    }

    // ── 신청 취소·좌석 변경 훅 ───────────────────────────────────────────────

    /** 신청 취소 훅: 확정 테이블에 앉아 있던 신청이면 그 참석을 CANCELED_EARLY로 바꾼다(참석 행이 있을 때만). */
    @Transactional
    public void cancelAttendance(UUID applicationId) {
        List<DiningTableMember> seats = diningTableMemberRepository.findByApplicationIdsWithTable(
                List.of(applicationId), EnumSet.of(DiningTableStatus.CONFIRMED));
        if (!seats.isEmpty()) {
            attendanceRepository.findByTableMemberIdIn(seats.stream().map(DiningTableMember::getId).toList())
                    .forEach(Attendance::cancelEarly);
        }
    }

    /**
     * 좌석 변경 훅(수동 배정·이동·충원 뒤 호출). 확정 테이블에 앉았으면 확정 파이프라인의 개인 규칙
     * (배정 회차 = 테이블 회차, 매칭 CONFIRMED, 참석 SCHEDULED는 없을 때만)을 적용하고, 확정 전 테이블로 옮겨졌으면 그 좌석의 참석 행을 지운다.
     * TODO(KAN-347 머지 후): DiningTableService의 seatedStatus 사용처(rebalance 충원·assignMember·move/merge)에서 새로 앉힌 멤버 행마다 부른다.
     */
    @Transactional
    public void seat(DiningTableMember member) {
        DiningTable table = member.getTable();
        if (table.getStatus() != DiningTableStatus.CONFIRMED) {
            releaseSeat(member.getId());
            return;
        }
        member.getApplication().confirmMatch(table.getSession());
        if (attendanceRepository.findByTableMemberId(member.getId()).isEmpty()) {
            attendanceRepository.save(Attendance.schedule(member.getId()));
        }
    }

    /**
     * 운영 조정(이동·제외)으로 좌석을 떠날 때 그 멤버 행의 참석 행을 지운다. 참가자가 취소한 게 아니므로 CANCELED_EARLY를 남기지 않고,
     * 옮겨 간 확정 테이블에서는 seat가 새 SCHEDULED를 만든다.
     * TODO(KAN-347 머지 후): 이동·병합으로 removed_at을 표시한 옛 멤버 행마다 부른다. 신청 취소로 빠지는 행은 부르지 않는다(CANCELED_EARLY 기록 유지).
     */
    @Transactional
    public void releaseSeat(UUID tableMemberId) {
        attendanceRepository.findByTableMemberId(tableMemberId).ifPresent(attendanceRepository::delete);
    }

    // ── 회차 종료 처리 ───────────────────────────────────────────────────────

    /**
     * 회차 종료(종료 시간, 없으면 시작 + 3시간) + 1시간이 지난 확정 테이블이 있는 회차를 회차마다 별도 트랜잭션으로 한 번 처리한다.
     * 한 회차의 실패가 다른 회차를 막지 않는다.
     */
    public void closeFinishedSessions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        List<UUID> sessionIds = diningTableRepository.findWithSessionByStatusAndEventDateBetween(
                        DiningTableStatus.CONFIRMED, today.minusDays(CLOSING_LOOKBACK_DAYS), today).stream()
                .map(DiningTable::getSession)
                .filter(session -> session.getClosingProcessedAt() == null
                        && session.getStatus() != GatheringSessionStatus.CANCELLED
                        && !session.getEndAt().plusHours(CLOSING_DELAY_HOURS).isAfter(now))
                .map(GatheringSession::getId)
                .distinct()
                .toList();
        for (UUID sessionId : sessionIds) {
            try {
                TransactionTemplate template = new TransactionTemplate(transactionManager);
                template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                template.executeWithoutResult(status -> closeSession(sessionId, now));
            } catch (Exception e) {
                log.error("회차 종료 처리 실패: sessionId={}", sessionId, e);
            }
        }
    }

    // 회차 행을 잠가 여러 인스턴스·재실행에서 한 번만 처리한다. 체크인 없는 SCHEDULED → 노쇼 후보, 확정 테이블 → DONE + 피드백 요청,
    // 남은 제안 테이블이 없으면(모든 테이블 DONE) 회차 DONE.
    private void closeSession(UUID sessionId, LocalDateTime now) {
        GatheringSession session = gatheringService.lockRandomTableSession(sessionId);
        if (session.getClosingProcessedAt() != null) {
            return;
        }
        session.markClosingProcessed(now);
        List<DiningTable> tables = diningTableRepository.findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(
                sessionId, EnumSet.of(DiningTableStatus.PROPOSED, DiningTableStatus.CONFIRMED));
        for (DiningTable table : tables) {
            if (table.getStatus() != DiningTableStatus.CONFIRMED) {
                continue;
            }
            List<DiningTableMember> members = findActiveMembers(table.getId());
            Map<UUID, Attendance> byMember = findAttendancesByMember(members);
            byMember.values().forEach(Attendance::markNoShowCandidate);
            table.done();
            // 피드백 요청: 취소 처리된 참석을 뺀 회원 멤버
            List<UUID> userIds = members.stream()
                    .filter(member -> !byMember.containsKey(member.getId())
                            || !CANCELED_STATUSES.contains(byMember.get(member.getId()).getStatus()))
                    .map(member -> member.getApplication().getUser())
                    .filter(Objects::nonNull)
                    .map(User::getId)
                    .distinct()
                    .toList();
            eventPublisher.publishEvent(new DiningTableLifecycleEvent(
                    NotificationType.DINING_FEEDBACK_REQUEST, table.getId(), sessionId, userIds));
        }
        if (tables.stream().allMatch(table -> table.getStatus() == DiningTableStatus.DONE)) {
            session.changeStatus(GatheringSessionStatus.DONE);
        }
    }

    // ── 다음 모집 알림 대상 ─────────────────────────────────────────────────

    /** since(포함) 이후 회차에 참석(ATTENDED)한 회원 → 참석했던 회차 장소 ID(장소 없는 회차는 빠진다). */
    @Transactional(readOnly = true)
    public Map<UUID, Set<UUID>> findRecentAttendeeLocations(LocalDate since) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        attendanceRepository.findAttendeeLocationsSince(AttendanceStatus.ATTENDED, since).forEach(row -> {
            Set<UUID> locationIds = result.computeIfAbsent(row.getUserId(), id -> new HashSet<>());
            if (row.getLocationId() != null) {
                locationIds.add(row.getLocationId());
            }
        });
        return result;
    }

    // ── 공통 ────────────────────────────────────────────────────────────────

    // 취소(soft delete)된 신청의 멤버 행은 뺀다.
    private List<DiningTableMember> findActiveMembers(UUID tableId) {
        return diningTableMemberRepository.findByTableIdWithApplication(tableId).stream()
                .filter(member -> member.getApplication().getDeletedAt() == null)
                .toList();
    }

    private Map<UUID, Attendance> findAttendancesByMember(List<DiningTableMember> members) {
        if (members.isEmpty()) {
            return Map.of();
        }
        return attendanceRepository.findByTableMemberIdIn(members.stream().map(DiningTableMember::getId).toList()).stream()
                .collect(Collectors.toMap(Attendance::getTableMemberId, Function.identity()));
    }
}
