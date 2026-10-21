package com.whatsuphouse.backend.domain.dining.repository;

import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import com.whatsuphouse.backend.domain.dining.enums.PriceRangeOption;
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
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V29 식당 시드(KAN-431). 운영 DB는 PostgreSQL이지만 같은 SQL을 H2(PostgreSQL 모드)에서 돌려 멱등·값 규칙을 확인한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class VenueSeedMigrationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<UUID> venuesBefore;

    // V29의 ALTER TABLE은 H2에서 열린 트랜잭션을 커밋해 이후 행이 롤백되지 않는다. 공유 H2에 남지 않도록 테스트 밖에서 지운다.
    @BeforeTransaction
    void snapshotVenues() {
        venuesBefore = jdbcTemplate.queryForList("SELECT id FROM venues", UUID.class);
    }

    @AfterTransaction
    void removeCommittedVenues() {
        jdbcTemplate.queryForList("SELECT id FROM venues", UUID.class).stream()
                .filter(id -> !venuesBefore.contains(id))
                .forEach(id -> jdbcTemplate.update("DELETE FROM venues WHERE id = ?", id));
    }

    @Test
    @DisplayName("지역별 5곳 20곳을 넣고, 같은 이름·지역의 미삭제 식당은 건너뛰며, 다시 돌려도 같다. 지역·가격대는 선택지 문구 그대로")
    void seed_idempotentAndUsesLabels() {
        // given: 운영자가 이미 넣은 같은 식당
        em.persistAndFlush(Venue.builder().name("브리비트 강남역점").address("직접 입력").region("강남/신논현").isActive(true).build());

        // when
        runV29();
        runV29();

        // then
        assertThat(count("SELECT COUNT(*) FROM venues WHERE name = '브리비트 강남역점'")).isEqualTo(1);
        for (String region : DiningRegion.labels()) {
            assertThat(count("SELECT COUNT(*) FROM venues WHERE region = '" + region + "'")).isEqualTo(5);
        }
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT price_range FROM venues WHERE map_url LIKE 'https://map.naver.com/%'", String.class))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(PriceRangeOption.values()).map(PriceRangeOption::getLabel).toList());
    }

    private void runV29() {
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new EncodedResource(
                new ClassPathResource("db/migration/V29__region_matching_and_venue_seed.sql"), StandardCharsets.UTF_8));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
