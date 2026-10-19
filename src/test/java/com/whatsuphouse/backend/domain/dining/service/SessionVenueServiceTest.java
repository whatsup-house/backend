package com.whatsuphouse.backend.domain.dining.service;

import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.enums.PriceRangeOption;
import com.whatsuphouse.backend.domain.dining.repository.SessionVenueRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SessionVenueServiceTest {

    @Mock
    private SessionVenueRepository sessionVenueRepository;
    @Mock
    private VenueRepository venueRepository;

    @InjectMocks
    private SessionVenueService sessionVenueService;

    private final UUID sessionId = UUID.randomUUID();

    @Test
    @DisplayName("선호 가격대에 드는 식당이 있으면 id 순서가 뒤여도 먼저 배정한다")
    void useFirstAvailableVenue_prefersMatchingPriceRange() {
        Venue cheap = venue("싼집", "10,000~20,000원");
        Venue mid = venue("중간집", "20,000~30,000원");
        givenPool(cheap, mid);

        Optional<UUID> result = sessionVenueService.useFirstAvailableVenue(
                sessionId, Set.of(PriceRangeOption.MID.getLabel()));

        assertThat(result).contains(mid.getId());
    }

    @Test
    @DisplayName("선호가 없거나 맞는 식당이 없으면 기존처럼 첫 식당을 배정한다")
    void useFirstAvailableVenue_noPreference_firstVenue() {
        Venue cheap = venue("싼집", "10,000~20,000원");
        Venue mid = venue("중간집", "20,000~30,000원");
        givenPool(cheap, mid);

        assertThat(sessionVenueService.useFirstAvailableVenue(sessionId, Set.of())).contains(cheap.getId());
        assertThat(sessionVenueService.useFirstAvailableVenue(
                sessionId, Set.of(PriceRangeOption.HIGH.getLabel()))).contains(cheap.getId());
    }

    @Test
    @DisplayName("가격대를 해석할 수 없는 식당은 선호와 상관없이 순서대로 배정한다")
    void useFirstAvailableVenue_unparseablePriceRange_treatedAsNoPreference() {
        Venue unknown = venue("가격 미정", "시가");
        Venue mid = venue("중간집", "20,000~30,000원");
        givenPool(unknown, mid);

        assertThat(sessionVenueService.useFirstAvailableVenue(
                sessionId, Set.of(PriceRangeOption.MID.getLabel()))).contains(mid.getId());
        assertThat(sessionVenueService.useFirstAvailableVenue(sessionId, Set.of())).contains(unknown.getId());
    }

    @Test
    @DisplayName("식당 가격대 문자열은 숫자 평균으로 선택지에 대응시킨다")
    void priceRangeOption_ofVenueText() {
        assertThat(PriceRangeOption.ofVenueText("10,000~25,000원")).contains(PriceRangeOption.LOW);
        assertThat(PriceRangeOption.ofVenueText("1만~2만원")).contains(PriceRangeOption.LOW);
        assertThat(PriceRangeOption.ofVenueText("25000")).contains(PriceRangeOption.MID);
        assertThat(PriceRangeOption.ofVenueText("30,000원 이상")).contains(PriceRangeOption.HIGH);
        assertThat(PriceRangeOption.ofVenueText("1인 2만원")).contains(PriceRangeOption.MID);
        assertThat(PriceRangeOption.ofVenueText("2인 이상 25,000원")).contains(PriceRangeOption.MID);
        assertThat(PriceRangeOption.ofVenueText("시가")).isEmpty();
        assertThat(PriceRangeOption.ofVenueText(null)).isEmpty();
    }

    private void givenPool(Venue... venues) {
        List<SessionVenue> pool = Arrays.stream(venues)
                .map(venue -> new SessionVenue(sessionId, venue.getId(), 2)).toList();
        given(sessionVenueRepository.findBySessionIdForUpdate(sessionId)).willReturn(pool);
        given(venueRepository.findAllById(anyList())).willReturn(List.of(venues));
    }

    private static Venue venue(String name, String priceRange) {
        Venue venue = Venue.builder().name(name).address("서울").priceRange(priceRange).region("강남").isActive(true).build();
        ReflectionTestUtils.setField(venue, "id", UUID.randomUUID());
        return venue;
    }
}
