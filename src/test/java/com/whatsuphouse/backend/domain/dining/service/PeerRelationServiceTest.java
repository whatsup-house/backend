package com.whatsuphouse.backend.domain.dining.service;

import com.whatsuphouse.backend.domain.dining.entity.PeerPreference;
import com.whatsuphouse.backend.domain.dining.entity.SafetyReport;
import com.whatsuphouse.backend.domain.dining.enums.PeerPreferenceKind;
import com.whatsuphouse.backend.domain.dining.repository.PeerPreferenceRepository;
import com.whatsuphouse.backend.domain.dining.repository.SafetyReportRepository;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestJpaConfig.class, PeerRelationService.class})
@ActiveProfiles("test")
class PeerRelationServiceTest {

    @Autowired
    private PeerRelationService peerRelationService;

    @Autowired
    private PeerPreferenceRepository peerPreferenceRepository;

    @Autowired
    private SafetyReportRepository safetyReportRepository;

    private final UUID a = new UUID(1, 1);
    private final UUID b = new UUID(1, 2);
    private final UUID c = new UUID(1, 3);
    private final UUID outsider = new UUID(1, 9);
    private final UUID tableId = UUID.randomUUID();

    @Test
    @DisplayName("제외 쌍은 AVOID 선호(단방향)와 신고(신고자→피신고자)를 한 번에 모으고, AGAIN·집합 밖 회원은 뺀다")
    void findExcludedPairs_avoidAndReports() {
        peerPreferenceRepository.save(new PeerPreference(a, b, PeerPreferenceKind.AVOID, tableId));
        peerPreferenceRepository.save(new PeerPreference(a, c, PeerPreferenceKind.AGAIN, tableId));
        peerPreferenceRepository.save(new PeerPreference(a, outsider, PeerPreferenceKind.AVOID, tableId));
        safetyReportRepository.save(new SafetyReport(c, b, tableId, "불쾌한 언행", null));
        safetyReportRepository.save(new SafetyReport(outsider, a, tableId, "불쾌한 언행", null));

        assertThat(peerRelationService.findExcludedPairs(List.of(a, b, c)))
                .containsOnly(entry(a, Set.of(b)), entry(c, Set.of(b)));
        assertThat(peerRelationService.findAgainPairs(List.of(a, b, c)))
                .containsOnly(entry(a, Set.of(c)));
        assertThat(peerRelationService.findExcludedPairs(List.of())).isEmpty();
    }
}
