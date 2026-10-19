package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.dining.enums.PriceRangeOption;
import com.whatsuphouse.backend.domain.dining.service.SessionVenueService;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/** 식당 배정의 가격대 우선(ACC-04): 테이블 멤버 PRICE_RANGE 답의 교집합을 식당 풀 배정에 넘긴다. (KAN-407) */
@ExtendWith(MockitoExtension.class)
class DiningConfirmServiceVenueTest {

    private static final String LOW = PriceRangeOption.LOW.getLabel();
    private static final String MID = PriceRangeOption.MID.getLabel();
    private static final String HIGH = PriceRangeOption.HIGH.getLabel();

    @Mock
    private DiningTableRepository diningTableRepository;
    @Mock
    private DiningTableMemberRepository diningTableMemberRepository;
    @Mock
    private AdminApplicationService adminApplicationService;
    @Mock
    private SessionVenueService sessionVenueService;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private DiningConfirmService diningConfirmService;

    private final UUID tableId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private DiningTable table;
    private List<Application> applications;

    @BeforeEach
    void setUp() {
        Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        GatheringSession session = GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().plusDays(2)).maxAttendees(12).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        table = DiningTable.builder().session(session).eventDate(session.getEventDate()).groupSize(3)
                .algorithmVersion("rule-v2").build();
        ReflectionTestUtils.setField(table, "id", tableId);
        applications = IntStream.range(0, 3).mapToObj(i -> {
            Application application = Application.builder().bookingNumber("WH-" + i).gathering(gathering)
                    .name("회원" + i).phone("01012345678").build();
            ReflectionTestUtils.setField(application, "id", new UUID(0, i));
            return application;
        }).toList();
        List<DiningTableMember> members = applications.stream()
                .map(application -> DiningTableMember.builder().application(application).table(table)
                        .seatOrder(1).assignReason(AssignReason.INITIAL).build())
                .toList();
        given(diningTableRepository.findById(tableId)).willReturn(Optional.of(table));
        given(diningTableMemberRepository.findByTableIdWithApplication(tableId)).willReturn(members);
        given(adminApplicationService.findAnswers(any())).willAnswer(invocation -> answers);
    }

    private List<ApplicationAnswer> answers = List.of();

    @Test
    @DisplayName("멤버 가격대 답의 교집합을 식당 배정에 넘기고, 배정된 식당을 테이블에 건다")
    void assignVenue_prefersPriceRangeIntersection() {
        UUID venueId = UUID.randomUUID();
        answers = List.of(
                priceAnswer(0, List.of(LOW, MID)),
                priceAnswer(1, List.of(MID, HIGH)),
                priceAnswer(2, List.of(MID)));
        given(sessionVenueService.useFirstAvailableVenue(sessionId, Set.of(MID))).willReturn(Optional.of(venueId));

        diningConfirmService.assignVenue(tableId);

        verify(sessionVenueService).useFirstAvailableVenue(sessionId, Set.of(MID));
        assertThat(table.getVenueId()).isEqualTo(venueId);
    }

    @Test
    @DisplayName("답한 멤버가 없으면 선호 없이(빈 집합) 기존 순서로 배정한다")
    void assignVenue_noAnswers_noPreference() {
        UUID venueId = UUID.randomUUID();
        answers = List.of();
        given(sessionVenueService.useFirstAvailableVenue(sessionId, Set.of())).willReturn(Optional.of(venueId));

        diningConfirmService.assignVenue(tableId);

        assertThat(table.getVenueId()).isEqualTo(venueId);
    }

    @Test
    @DisplayName("교집합이 비면 선호 없음으로 본다")
    void assignVenue_disjointAnswers_noPreference() {
        answers = List.of(priceAnswer(0, List.of(LOW)), priceAnswer(1, List.of(HIGH)));
        given(sessionVenueService.useFirstAvailableVenue(sessionId, Set.of())).willReturn(Optional.of(UUID.randomUUID()));

        diningConfirmService.assignVenue(tableId);

        verify(sessionVenueService).useFirstAvailableVenue(sessionId, Set.of());
    }

    private ApplicationAnswer priceAnswer(int applicationNo, List<String> choices) {
        FormQuestion question = FormQuestion.builder().questionKey("price_range").type(QuestionType.MULTI_CHOICE)
                .label("가격대").reservedKey(ReservedQuestionKey.PRICE_RANGE).build();
        return ApplicationAnswer.builder().application(applications.get(applicationNo)).question(question)
                .value(Map.of("value", choices)).build();
    }
}
