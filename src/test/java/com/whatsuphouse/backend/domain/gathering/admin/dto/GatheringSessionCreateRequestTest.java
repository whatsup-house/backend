package com.whatsuphouse.backend.domain.gathering.admin.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringSessionCreateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatheringSessionCreateRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final UUID locationId = UUID.randomUUID();

    @Test
    @DisplayName("단건 형태: 회차 필드를 최상위로 받는다")
    void deserialize_flatShape_readsTopLevelFields() throws Exception {
        // given
        String json = """
                {"eventDate":"2026-10-10","locationId":"%s","maxAttendees":8,"priceOverride":20000}
                """.formatted(locationId);

        // when
        GatheringSessionCreateRequest request = objectMapper.readValue(json, GatheringSessionCreateRequest.class);

        // then
        assertThat(request.getEventDate()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(request.getPriceOverride()).isEqualTo(20000);
        assertThat(request.getRepeatWeekly()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("반복 형태: {base, repeatWeekly}의 base 필드를 최상위로 옮겨 같은 규칙으로 검증한다")
    void deserialize_repeatShape_unwrapsBase() throws Exception {
        // given
        String json = """
                {"base":{"eventDate":"2026-10-10","locationId":"%s","maxAttendees":8},
                 "repeatWeekly":{"until":"2026-12-26"}}
                """.formatted(locationId);

        // when
        GatheringSessionCreateRequest request = objectMapper.readValue(json, GatheringSessionCreateRequest.class);

        // then
        assertThat(request.getEventDate()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(request.getLocationId()).isEqualTo(locationId);
        assertThat(request.getRepeatWeekly().getUntil()).isEqualTo(LocalDate.of(2026, 12, 26));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("반복 형태에서 base 필수 필드가 빠지면 검증 실패")
    void validate_repeatShapeMissingBaseField_fails() throws Exception {
        // given
        String json = """
                {"base":{"locationId":"%s","maxAttendees":8},"repeatWeekly":{"until":"2026-12-26"}}
                """.formatted(locationId);

        // when
        GatheringSessionCreateRequest request = objectMapper.readValue(json, GatheringSessionCreateRequest.class);

        // then
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("eventDate");
    }
}
