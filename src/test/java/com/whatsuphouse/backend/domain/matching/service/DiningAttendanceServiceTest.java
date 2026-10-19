package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.entity.Attendance;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.repository.AttendanceRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DiningAttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private DiningTableRepository diningTableRepository;

    @Mock
    private DiningTableMemberRepository diningTableMemberRepository;

    @Mock
    private DepositService depositService;

    @InjectMocks
    private DiningAttendanceService diningAttendanceService;

    @Test
    @DisplayName("참가 횟수: 빈 입력이면 조회 없이 빈 Map (KAN-392)")
    void countAttendedByUserIds_empty() {
        assertThat(diningAttendanceService.countAttendedByUserIds(List.of())).isEmpty();
        verifyNoInteractions(attendanceRepository);
    }

    @Test
    @DisplayName("참가 횟수: ATTENDED 집계 결과를 회원 ID → 건수 Map으로 돌려준다 (KAN-392)")
    void countAttendedByUserIds() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID none = UUID.randomUUID();
        given(attendanceRepository.countByUserIdsAndStatus(List.of(first, second, none), AttendanceStatus.ATTENDED))
                .willReturn(List.of(row(first, 3L), row(second, 1L)));

        assertThat(diningAttendanceService.countAttendedByUserIds(List.of(first, second, none)))
                .isEqualTo(Map.of(first, 3L, second, 1L));
    }

    // ── 노쇼 보증금 훅 (KAN-402) ────────────────────────────────────────────

    @Test
    @DisplayName("운영자가 노쇼를 확정하면 회원을 보증금 대상으로 지정하고 그 신청의 보증금을 몰수한다")
    void noShowConfirmed_marksDepositRequired() {
        DiningTableMember member = seatedMember(LocalDateTime.now().minusHours(5));
        Attendance attendance = attendanceOf(member);
        given(attendanceRepository.findById(attendance.getId())).willReturn(Optional.of(attendance));
        given(diningTableMemberRepository.findById(member.getId())).willReturn(Optional.of(member));

        diningAttendanceService.changeAttendanceStatus(attendance.getId(), AttendanceStatus.NO_SHOW, UUID.randomUUID());

        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.NO_SHOW);
        assertThat(member.getApplication().getUser().isDepositRequired()).isTrue();
        then(depositService).should().forfeit(member.getApplication().getId());
        then(depositService).should(never()).requestReturn(any());
    }

    @Test
    @DisplayName("운영자가 지각 취소(CANCELED_LATE)로 확정하면 보증금·보증금 대상 지정은 건드리지 않는다")
    void canceledLateByAdmin_leavesDeposit() {
        DiningTableMember member = seatedMember(LocalDateTime.now().minusHours(5));
        Attendance attendance = attendanceOf(member);
        given(attendanceRepository.findById(attendance.getId())).willReturn(Optional.of(attendance));
        given(diningTableMemberRepository.findById(member.getId())).willReturn(Optional.of(member));

        diningAttendanceService.changeAttendanceStatus(attendance.getId(), AttendanceStatus.CANCELED_LATE, UUID.randomUUID());

        assertThat(member.getApplication().getUser().isDepositRequired()).isFalse();
        verifyNoInteractions(depositService);
    }

    @Test
    @DisplayName("본인 체크인하면 보증금 반환을 요청한다")
    void checkIn_requestsDepositReturn() {
        DiningTableMember member = seatedMember(LocalDateTime.now().plusMinutes(30));
        Attendance attendance = attendanceOf(member);
        DiningTable table = member.getTable();
        given(diningTableRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(diningTableMemberRepository.findByTableIdWithApplication(table.getId())).willReturn(List.of(member));
        given(attendanceRepository.findByTableMemberId(member.getId())).willReturn(Optional.of(attendance));

        diningAttendanceService.checkIn(table.getId(), member.getApplication().getUser().getId());

        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ATTENDED);
        then(depositService).should().requestReturn(member.getApplication().getId());
    }

    @Test
    @DisplayName("마감 후 취소 훅은 예정 참석을 CANCELED_LATE로, 마감 전은 CANCELED_EARLY로 바꾼다")
    void cancelAttendance_lateOrEarly() {
        DiningTableMember member = seatedMember(LocalDateTime.now().plusDays(1));
        Attendance late = attendanceOf(member);
        Attendance early = attendanceOf(member);
        UUID applicationId = member.getApplication().getId();
        given(diningTableMemberRepository.findByApplicationIdsWithTable(
                List.of(applicationId), EnumSet.of(DiningTableStatus.CONFIRMED))).willReturn(List.of(member));
        given(attendanceRepository.findByTableMemberIdIn(List.of(member.getId())))
                .willReturn(List.of(late), List.of(early));

        diningAttendanceService.cancelAttendance(applicationId, true);
        diningAttendanceService.cancelAttendance(applicationId, false);

        assertThat(late.getStatus()).isEqualTo(AttendanceStatus.CANCELED_LATE);
        assertThat(early.getStatus()).isEqualTo(AttendanceStatus.CANCELED_EARLY);
    }

    // 확정 테이블에 앉은 회원 1명. startAt: 회차 시작 시각.
    private DiningTableMember seatedMember(LocalDateTime startAt) {
        User user = User.builder().email("t@example.com").password("p").name("김철수").gender(Gender.MALE)
                .age(28).nickname("chulsoo").phone("01012345678").build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        GatheringSession session = GatheringSession.builder().gathering(gathering)
                .eventDate(startAt.toLocalDate()).maxAttendees(6).build();
        ReflectionTestUtils.setField(session, "startTime", startAt.toLocalTime());
        Application application = Application.builder().bookingNumber("WH261019-ATT001").gathering(gathering)
                .user(user).name("김철수").phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.confirm();
        DiningTable table = DiningTable.builder().session(session).eventDate(session.getEventDate()).groupSize(4)
                .algorithmVersion("rule-v2").build();
        ReflectionTestUtils.setField(table, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(table, "status", DiningTableStatus.CONFIRMED);
        DiningTableMember member = DiningTableMember.builder().application(application).table(table).seatOrder(1).build();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        return member;
    }

    private Attendance attendanceOf(DiningTableMember member) {
        Attendance attendance = Attendance.schedule(member.getId());
        ReflectionTestUtils.setField(attendance, "id", UUID.randomUUID());
        return attendance;
    }

    private AttendanceRepository.UserCountProjection row(UUID userId, long count) {
        return new AttendanceRepository.UserCountProjection() {
            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }
}
