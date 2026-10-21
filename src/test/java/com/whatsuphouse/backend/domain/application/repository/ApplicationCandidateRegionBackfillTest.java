package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.enums.LocationStatus;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V28 희망 지역 백필(KAN-430). 운영 DB는 PostgreSQL이지만 같은 SQL을 H2(PostgreSQL 모드)에서 돌려 규칙을 확인한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class ApplicationCandidateRegionBackfillTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int bookingSeq;

    @Test
    @DisplayName("희망 회차 장소명이 지역 라벨이면 그 지역들, 하나도 안 맞으면 4개 전부. 일반 모임·희망 회차 없는 신청은 건너뛰고, 다시 돌려도 같다")
    void backfill_regionsFromCandidateLocations() {
        // given
        Gathering randomTable = em.persist(Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
        Gathering regular = em.persist(Gathering.builder().title("재즈 게더링").build());
        Location gangnam = em.persist(location("강남/신논현"));
        Location jamsil = em.persist(location(" 잠실 "));
        Location cafe = em.persist(location("카페 재즈"));
        Application matched = application(randomTable,
                session(randomTable, gangnam), session(randomTable, jamsil), session(randomTable, cafe));
        Application unmatched = application(randomTable, session(randomTable, cafe), session(randomTable, null));
        Application withoutCandidates = application(randomTable);
        Application regularApplication = application(regular, session(regular, gangnam));
        em.flush();

        // when
        runV28();
        runV28();

        // then
        assertThat(regionsOf(matched)).containsExactlyInAnyOrder("강남/신논현", "잠실");
        assertThat(regionsOf(unmatched)).containsExactlyInAnyOrderElementsOf(DiningRegion.labels());
        assertThat(regionsOf(withoutCandidates)).isEmpty();
        assertThat(regionsOf(regularApplication)).isEmpty();
        // 저장된 라벨을 엔티티가 지역으로 읽는다
        em.clear();
        assertThat(em.find(Application.class, matched.getId()).getCandidateRegions())
                .containsExactlyInAnyOrder(DiningRegion.GANGNAM, DiningRegion.JAMSIL);
    }

    private void runV28() {
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new EncodedResource(
                new ClassPathResource("db/migration/V28__application_candidate_regions.sql"), StandardCharsets.UTF_8));
    }

    private List<String> regionsOf(Application application) {
        return jdbcTemplate.queryForList(
                "SELECT region FROM application_candidate_regions WHERE application_id = ?", String.class,
                application.getId());
    }

    private Application application(Gathering kind, GatheringSession... candidates) {
        Application application = em.persist(Application.builder()
                .bookingNumber("WH-BF-" + (++bookingSeq))
                .gathering(kind)
                .name("신청자")
                .phone("01012345678")
                .build());
        for (int i = 0; i < candidates.length; i++) {
            em.persist(new ApplicationCandidateSession(application, candidates[i], i + 1));
        }
        return application;
    }

    private GatheringSession session(Gathering kind, Location location) {
        return em.persist(GatheringSession.builder()
                .gathering(kind).location(location).eventDate(LocalDate.now().plusDays(7)).maxAttendees(24).build());
    }

    private static Location location(String name) {
        return Location.builder().name(name).address("서울").status(LocationStatus.ACTIVE).maxCapacity(20).build();
    }
}
