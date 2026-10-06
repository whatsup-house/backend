package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.entity.Attendance;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class AttendanceRepositoryTest {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private TestEntityManager em;

    private int seq;

    private User user() {
        int no = ++seq;
        return em.persist(User.builder().email("u" + no + "@example.com").password("encoded").name("회원" + no)
                .gender(Gender.MALE).age(30).nickname("회원" + no).build());
    }

    // 회원이 한 회차 테이블에 앉아 status로 참석 행을 남긴다.
    private Application seat(User user, AttendanceStatus status) {
        int no = ++seq;
        Gathering gathering = em.persist(Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
        GatheringSession session = em.persist(GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().minusDays(no)).maxAttendees(12).build());
        Application application = em.persist(Application.builder().bookingNumber("WH-" + no).gathering(gathering).user(user)
                .name(user.getName()).phone("010123456" + no).build());
        DiningTable table = em.persist(DiningTable.builder().session(session).eventDate(session.getEventDate())
                .groupSize(1).algorithmVersion("rule-v2").build());
        table.confirm();
        DiningTableMember member = em.persist(DiningTableMember.builder()
                .application(application).table(table).assignReason(AssignReason.INITIAL).build());
        Attendance attendance = Attendance.schedule(member.getId());
        if (status == AttendanceStatus.ATTENDED) {
            attendance.checkIn(LocalDateTime.now());
        } else if (status != AttendanceStatus.SCHEDULED) {
            attendance.changeStatus(status, null);
        }
        em.persist(attendance);
        return application;
    }

    @Test
    @DisplayName("회원별 참석 건수는 ATTENDED만 세고, 참석이 없는 회원은 결과에 없다 (KAN-392)")
    void countByUserIdsAndStatus() {
        User twice = user();
        User once = user();
        User none = user();
        User outside = user(); // 조회 대상이 아닌 회원
        seat(twice, AttendanceStatus.ATTENDED);
        seat(twice, AttendanceStatus.ATTENDED);
        seat(twice, AttendanceStatus.NO_SHOW);
        seat(once, AttendanceStatus.ATTENDED);
        seat(once, AttendanceStatus.SCHEDULED);
        seat(none, AttendanceStatus.NO_SHOW);
        seat(none, AttendanceStatus.CANCELED_LATE);
        seat(outside, AttendanceStatus.ATTENDED);
        em.flush();
        em.clear();

        assertThat(attendanceRepository.countByUserIdsAndStatus(
                List.of(twice.getId(), once.getId(), none.getId()), AttendanceStatus.ATTENDED))
                .extracting(AttendanceRepository.UserCountProjection::getUserId,
                        AttendanceRepository.UserCountProjection::getCount)
                .containsExactlyInAnyOrder(tuple(twice.getId(), 2L), tuple(once.getId(), 1L));
    }

    @Test
    @DisplayName("삭제(soft delete)된 신청의 참석은 세지 않는다 (KAN-392)")
    void countByUserIdsAndStatus_deletedApplication_excluded() {
        User user = user();
        seat(user, AttendanceStatus.ATTENDED);
        seat(user, AttendanceStatus.ATTENDED).delete();
        em.flush();
        em.clear();

        assertThat(attendanceRepository.countByUserIdsAndStatus(List.of(user.getId()), AttendanceStatus.ATTENDED))
                .extracting(AttendanceRepository.UserCountProjection::getCount)
                .containsExactly(1L);
    }
}
