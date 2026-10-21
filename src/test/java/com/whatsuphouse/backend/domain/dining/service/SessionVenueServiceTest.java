package com.whatsuphouse.backend.domain.dining.service;

import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.enums.PriceRangeOption;
import com.whatsuphouse.backend.domain.dining.repository.SessionVenueRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SessionVenueServiceTest {

    private static final String GANGNAM = "강남/신논현";
    private static final String JAMSIL = "잠실";
    private static final String LOW = "10,000~20,000원";
    private static final String MID = "20,000~30,000원";

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
        // given
        Venue cheap = venue("싼집", LOW, GANGNAM);
        Venue mid = venue("중간집", MID, GANGNAM);
        givenPool(cheap, mid);

        // when
        Optional<UUID> result = sessionVenueService.useFirstAvailableVenue(
                sessionId, null, Set.of(PriceRangeOption.MID.getLabel()));

        // then
        assertThat(result).contains(mid.getId());
    }

    @Test
    @DisplayName("선호가 없거나 맞는 식당이 없으면 기존처럼 첫 식당을 배정한다")
    void useFirstAvailableVenue_noPreference_firstVenue() {
        // given
        Venue cheap = venue("싼집", LOW, GANGNAM);
        Venue mid = venue("중간집", MID, GANGNAM);
        givenPool(cheap, mid);

        // when & then
        assertThat(sessionVenueService.useFirstAvailableVenue(sessionId, null, Set.of())).contains(cheap.getId());
        assertThat(sessionVenueService.useFirstAvailableVenue(
                sessionId, null, Set.of(PriceRangeOption.HIGH.getLabel()))).contains(cheap.getId());
    }

    @Test
    @DisplayName("가격대를 해석할 수 없는 식당은 선호와 상관없이 순서대로 배정한다")
    void useFirstAvailableVenue_unparseablePriceRange_treatedAsNoPreference() {
        // given
        Venue unknown = venue("가격 미정", "시가", GANGNAM);
        Venue mid = venue("중간집", MID, GANGNAM);
        givenPool(unknown, mid);

        // when & then
        assertThat(sessionVenueService.useFirstAvailableVenue(
                sessionId, null, Set.of(PriceRangeOption.MID.getLabel()))).contains(mid.getId());
        assertThat(sessionVenueService.useFirstAvailableVenue(sessionId, null, Set.of())).contains(unknown.getId());
    }

    @Test
    @DisplayName("테이블 지역이 있으면 풀에서 그 지역 식당만 보고, 그 안에서 가격대 우선이다 (KAN-431)")
    void useFirstAvailableVenue_region_filtersPoolAndPrefersPrice() {
        // given
        Venue jamsilMid = venue("잠실 중간집", MID, JAMSIL);
        Venue gangnamCheap = venue("강남 싼집", LOW, GANGNAM);
        Venue gangnamMid = venue("강남 중간집", MID, GANGNAM);
        givenPool(jamsilMid, gangnamCheap, gangnamMid);

        // when & then
        assertThat(sessionVenueService.useFirstAvailableVenue(sessionId, GANGNAM, Set.of(MID))).contains(gangnamMid.getId());
        assertThat(sessionVenueService.useFirstAvailableVenue(sessionId, GANGNAM, Set.of())).contains(gangnamCheap.getId());
        then(venueRepository).should(never()).findAllByRegionAndDeletedAtIsNullOrderByIdAsc(any());
    }

    @Test
    @DisplayName("풀에 그 지역 식당이 없으면 지역 활성 식당을 수용 1로 풀에 넣고(이미 있는 행·비활성 제외) 가격대 우선으로 배정한다 (KAN-431)")
    void useFirstAvailableVenue_noRegionRows_autoAddsRegionVenues() {
        // given
        Venue jamsil = venue("잠실집", LOW, JAMSIL);
        givenPool(jamsil);
        Venue cheap = venue("강남 싼집", LOW, GANGNAM);
        Venue mid = venue("강남 중간집", MID, GANGNAM);
        Venue closed = venue("강남 폐업", MID, GANGNAM);
        closed.update(closed.getName(), closed.getAddress(), null, MID, GANGNAM, false);
        given(venueRepository.findAllByRegionAndDeletedAtIsNullOrderByIdAsc(GANGNAM)).willReturn(List.of(cheap, mid, closed));
        given(sessionVenueRepository.save(any(SessionVenue.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Optional<UUID> result = sessionVenueService.useFirstAvailableVenue(sessionId, GANGNAM, Set.of(MID));

        // then
        assertThat(result).contains(mid.getId());
        ArgumentCaptor<SessionVenue> added = ArgumentCaptor.forClass(SessionVenue.class);
        then(sessionVenueRepository).should(times(2)).save(added.capture());
        assertThat(added.getAllValues()).extracting(SessionVenue::getVenueId).containsExactly(cheap.getId(), mid.getId());
        assertThat(added.getAllValues()).allSatisfy(sv -> assertThat(sv.getCapacityTables()).isEqualTo(1));
        assertThat(added.getAllValues().get(1).getUsedTables()).isEqualTo(1);
    }

    @Test
    @DisplayName("운영자가 넣은 그 지역 식당이 다 찼으면 자동 추가하지 않고 empty (VENUE 예외 건 경로) (KAN-431)")
    void useFirstAvailableVenue_regionRowsFull_noAutoAdd() {
        // given
        Venue full = venue("강남 만석", LOW, GANGNAM);
        List<SessionVenue> pool = givenPool(full);
        pool.get(0).useTable();
        pool.get(0).useTable();

        // when & then
        assertThat(sessionVenueService.useFirstAvailableVenue(sessionId, GANGNAM, Set.of())).isEmpty();
        then(venueRepository).should(never()).findAllByRegionAndDeletedAtIsNullOrderByIdAsc(any());
        then(sessionVenueRepository).should(never()).save(any());
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

    // 수용 2테이블 풀
    private List<SessionVenue> givenPool(Venue... venues) {
        List<SessionVenue> pool = new ArrayList<>(Arrays.stream(venues)
                .map(venue -> new SessionVenue(sessionId, venue.getId(), 2)).toList());
        given(sessionVenueRepository.findBySessionIdForUpdate(sessionId)).willReturn(pool);
        given(venueRepository.findAllById(anyList())).willReturn(List.of(venues));
        return pool;
    }

    private static Venue venue(String name, String priceRange, String region) {
        Venue venue = Venue.builder().name(name).address("서울").priceRange(priceRange).region(region).isActive(true).build();
        ReflectionTestUtils.setField(venue, "id", UUID.randomUUID());
        return venue;
    }
}
