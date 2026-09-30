package com.whatsuphouse.backend.domain.dining.repository;

import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.entity.MatchingRuleSetting;
import com.whatsuphouse.backend.domain.dining.entity.MatchingWeights;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class DiningRepositoryTest {

    @Autowired
    private ExceptionCaseRepository exceptionCaseRepository;

    @Autowired
    private MatchingRuleSettingRepository matchingRuleSettingRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("예외함 필터는 null인 조건을 걸지 않는다")
    void findAllByFilter_ignoresNullConditions() {
        exceptionCaseRepository.save(ExceptionCase.open(ExceptionCaseType.VENUE, UUID.randomUUID(), null, null, "식당 부족"));
        exceptionCaseRepository.save(ExceptionCase.open(ExceptionCaseType.SAFETY, null, null, UUID.randomUUID(), "신고"));
        ExceptionCase resolved = ExceptionCase.open(ExceptionCaseType.VENUE, null, null, null, "식당 부족");
        resolved.resolve(UUID.randomUUID(), "수동 배정", null);
        exceptionCaseRepository.save(resolved);
        em.flush();
        em.clear();

        assertThat(exceptionCaseRepository.findAllByFilter(null, null)).hasSize(3);
        assertThat(exceptionCaseRepository.findAllByFilter(ExceptionCaseType.VENUE, null)).hasSize(2);
        assertThat(exceptionCaseRepository.findAllByFilter(null, ExceptionCaseStatus.OPEN)).hasSize(2);
        assertThat(exceptionCaseRepository.findAllByFilter(ExceptionCaseType.VENUE, ExceptionCaseStatus.RESOLVED))
                .singleElement()
                .satisfies(found -> assertThat(found.getResolutionNote()).isEqualTo("수동 배정"));
    }

    @Test
    @DisplayName("매칭 규칙 가중치는 JSON으로 저장했다가 그대로 읽힌다")
    void matchingRuleSetting_weightsRoundTrip() {
        MatchingRuleSetting setting = matchingRuleSettingRepository.save(MatchingRuleSetting.defaults());
        setting.update(6, 3, 5, new BigDecimal("0.4000"), 0, new MatchingWeights(0.5, 1.0, 2.0, 1.5, 0.0));
        em.flush();
        em.clear();

        MatchingRuleSetting found = matchingRuleSettingRepository.findById(MatchingRuleSetting.SINGLETON_ID).orElseThrow();
        assertThat(found.getTableSizeMin()).isEqualTo(3);
        assertThat(found.getAutoConfirmGraceMinutes()).isZero();
        assertThat(found.getWeights().getInterests()).isEqualTo(2.0);
        assertThat(found.getWeights().getWantedStyle()).isEqualTo(1.5);
    }
}
