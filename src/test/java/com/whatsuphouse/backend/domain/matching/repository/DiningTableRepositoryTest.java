package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.entity.MatchRun;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.enums.MatchRunTrigger;
import com.whatsuphouse.backend.domain.matching.enums.UnassignedReason;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class DiningTableRepositoryTest {

    private static final EnumSet<DiningTableStatus> MET = EnumSet.of(DiningTableStatus.CONFIRMED, DiningTableStatus.DONE);

    @Autowired
    private DiningTableMemberRepository diningTableMemberRepository;

    @Autowired
    private MatchRunRepository matchRunRepository;

    @Autowired
    private TestEntityManager em;

    private GatheringSession session;
    private Gathering gathering;

    @BeforeEach
    void setUp() {
        gathering = em.persist(Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
        session = em.persist(GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().plusDays(7)).maxAttendees(12).build());
    }

    private Application application(int no) {
        User user = em.persist(User.builder().email("u" + no + "@example.com").password("encoded").name("회원" + no)
                .gender(Gender.MALE).age(30).nickname("회원" + no).build());
        return em.persist(Application.builder().bookingNumber("WH-" + no).gathering(gathering).user(user)
                .name("회원" + no).phone("0101234567" + no).build());
    }

    private DiningTable table(DiningTableStatus status, Application... members) {
        DiningTable table = em.persist(DiningTable.builder().session(session).eventDate(session.getEventDate())
                .groupSize(members.length).algorithmVersion("rule-v2").build());
        if (status == DiningTableStatus.CONFIRMED) {
            table.confirm();
        } else if (status == DiningTableStatus.DISSOLVED) {
            table.dissolve();
        }
        for (Application member : members) {
            em.persist(DiningTableMember.builder().application(member).table(table).assignReason(AssignReason.INITIAL).build());
        }
        return table;
    }

    @Test
    @DisplayName("이전 만남 쌍은 확정·완료 테이블에서만 양방향으로 나오고, 지정한 테이블은 뺀다")
    void findUserPairsByTableStatusIn() {
        Application a = application(1);
        Application b = application(2);
        Application c = application(3);
        DiningTable confirmed = table(DiningTableStatus.CONFIRMED, a, b);
        table(DiningTableStatus.DISSOLVED, b, c);
        em.flush();
        em.clear();
        List<UUID> userIds = List.of(a.getUser().getId(), b.getUser().getId(), c.getUser().getId());

        assertThat(diningTableMemberRepository.findUserPairsByTableStatusIn(userIds, MET, null))
                .extracting(DiningTableMemberRepository.UserPairProjection::getUserId,
                        DiningTableMemberRepository.UserPairProjection::getOtherUserId)
                .containsExactlyInAnyOrder(tuple(a.getUser().getId(), b.getUser().getId()),
                        tuple(b.getUser().getId(), a.getUser().getId()));
        assertThat(diningTableMemberRepository.findUserPairsByTableStatusIn(userIds, MET, confirmed.getId())).isEmpty();
    }

    @Test
    @DisplayName("해체된 테이블에 앉았던 신청은 활성 테이블 착석으로 보지 않는다")
    void findApplicationIdsByTableStatusIn() {
        Application seated = application(1);
        Application released = application(2);
        table(DiningTableStatus.PROPOSED, seated);
        table(DiningTableStatus.DISSOLVED, released, seated);
        em.flush();
        em.clear();

        assertThat(diningTableMemberRepository.findApplicationIdsByTableStatusIn(
                List.of(seated.getId(), released.getId()),
                EnumSet.of(DiningTableStatus.PROPOSED, DiningTableStatus.CONFIRMED)))
                .containsExactly(seated.getId());
    }

    @Test
    @DisplayName("매칭 실행의 미배정 사유 목록은 JSON으로 저장했다가 그대로 읽힌다")
    void matchRun_unassignedReasonsRoundTrip() {
        UUID applicationId = UUID.randomUUID();
        MatchRun run = MatchRun.start(session.getId(), MatchRunTrigger.MANUAL, null, "rule-v2");
        run.finish(5, 1, 0, 0, 0, List.of(new MatchRun.Unassigned(applicationId, UnassignedReason.AGE_GAP)));
        matchRunRepository.save(run);
        em.flush();
        em.clear();

        MatchRun found = matchRunRepository.findFirstBySessionIdOrderByStartedAtDesc(session.getId()).orElseThrow();
        assertThat(found.getUnassignedReasons())
                .containsExactly(new MatchRun.Unassigned(applicationId, UnassignedReason.AGE_GAP));
        assertThat(found.getUnassignedCount()).isEqualTo(1);
        assertThat(matchRunRepository.findSessionIdsIn(List.of(session.getId(), UUID.randomUUID())))
                .containsExactly(session.getId());
    }
}
