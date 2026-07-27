package com.whatsuphouse.backend.domain.location.repository;

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
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class LocationRepositoryTest {

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("삭제된 장소는 목록 조회에서 제외")
    void findByDeletedAtIsNull_excludesDeleted() {
        saveLocation("재즈바 A");
        Location deleted = saveLocation("삭제된 장소");
        deleted.delete();
        em.flush();
        em.clear();

        List<Location> result = locationRepository.findByDeletedAtIsNull();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("재즈바 A");
    }

    @Test
    @DisplayName("삭제된 장소 단건 조회 시 빈 Optional 반환")
    void findByIdAndDeletedAtIsNull_excludesDeleted() {
        Location location = saveLocation("삭제될 장소");
        UUID id = location.getId();
        location.delete();
        em.flush();
        em.clear();

        Optional<Location> result = locationRepository.findByIdAndDeletedAtIsNull(id);

        assertThat(result).isEmpty();
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private Location saveLocation(String name) {
        return locationRepository.save(Location.builder()
                .name(name)
                .address("서울시 마포구 합정동 123")
                .status(LocationStatus.ACTIVE)
                .maxCapacity(20)
                .build());
    }
}
