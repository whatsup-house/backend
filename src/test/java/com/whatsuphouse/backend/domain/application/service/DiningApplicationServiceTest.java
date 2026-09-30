package com.whatsuphouse.backend.domain.application.service;

import com.whatsuphouse.backend.domain.application.client.dto.response.DiningPrefillResponse;
import com.whatsuphouse.backend.domain.application.client.service.DiningApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.form.client.service.FormService;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DiningApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationCandidateSessionRepository applicationCandidateSessionRepository;

    @Mock
    private ApplicationAnswerRepository applicationAnswerRepository;

    @Mock
    private TicketService ticketService;

    @Mock
    private MatchingService matchingService;

    @Mock
    private FormService formService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DiningApplicationService diningApplicationService;

    private UUID userId;
    private UUID applicationId;
    private User user;
    private Gathering randomTable;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        applicationId = UUID.randomUUID();
        user = User.builder()
                .email("test@example.com")
                .password("encoded")
                .name("김철수")
                .gender(Gender.MALE)
                .age(28)
                .nickname("chulsoo")
                .phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        randomTable = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        ReflectionTestUtils.setField(randomTable, "id", UUID.randomUUID());
    }

    // ── cancelApplication() ────────────────────────────────────────────────

    @Test
    @DisplayName("배정 회차 시작 2일 전보다 이르면 취소되고 이용권이 복원된다")
    void cancel_assignedSessionBeforeWindow_cancelsAndRestoresTicket() {
        Application application = confirmedApplication(sessionOn(3));
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));

        diningApplicationService.cancelApplication(applicationId, userId);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        then(ticketService).should().refundOneTicket(application);
        then(eventPublisher).should().publishEvent(any(ApplicationCancelledEvent.class));
    }

    @Test
    @DisplayName("배정 회차 시작 2일 이내면 CANCEL_WINDOW_CLOSED")
    void cancel_assignedSessionWithinWindow_throws() {
        Application application = confirmedApplication(sessionOn(1));
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> diningApplicationService.cancelApplication(applicationId, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCEL_WINDOW_CLOSED);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        then(ticketService).should(never()).refundOneTicket(any());
    }

    @Test
    @DisplayName("배정 전(WAITING)이면 아직 시작하지 않은 희망 회차 중 가장 이른 회차가 기준이다")
    void cancel_waiting_usesEarliestUpcomingCandidate() {
        Application application = confirmedApplication(null);
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));
        // 1순위는 다음 주, 2순위는 내일: 내일 회차가 기준이라 기한이 지났다.
        given(applicationCandidateSessionRepository.findWithSessionByApplicationIds(List.of(applicationId)))
                .willReturn(List.of(candidate(application, sessionOn(7), 1), candidate(application, sessionOn(1), 2)));

        assertThatThrownBy(() -> diningApplicationService.cancelApplication(applicationId, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANCEL_WINDOW_CLOSED);
    }

    @Test
    @DisplayName("이미 지난 희망 회차는 취소 기한 기준에서 빠진다")
    void cancel_waiting_ignoresPastCandidate() {
        Application application = confirmedApplication(null);
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));
        given(applicationCandidateSessionRepository.findWithSessionByApplicationIds(List.of(applicationId)))
                .willReturn(List.of(candidate(application, sessionOn(-1), 1), candidate(application, sessionOn(5), 2)));

        diningApplicationService.cancelApplication(applicationId, userId);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        then(ticketService).should().refundOneTicket(application);
    }

    @Test
    @DisplayName("본인 신청이 아니면 APPLICATION_FORBIDDEN(403)")
    void cancel_otherUser_throwsForbidden() {
        Application application = confirmedApplication(sessionOn(5));
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> diningApplicationService.cancelApplication(applicationId, UUID.randomUUID()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_FORBIDDEN);
    }

    @Test
    @DisplayName("이미 취소한 신청이면 APPLICATION_ALREADY_CANCELLED(409)")
    void cancel_alreadyCancelled_throwsConflict() {
        Application application = confirmedApplication(sessionOn(5));
        application.cancel();
        given(applicationRepository.findIncludingDeletedById(applicationId)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> diningApplicationService.cancelApplication(applicationId, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_ALREADY_CANCELLED);
        then(ticketService).should(never()).refundOneTicket(any());
    }

    // ── getPrefill() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("프리필은 이 모임 폼의 표준 질문마다 가장 최근 답변을 이 폼의 질문 키로 돌려준다")
    void getPrefill_returnsLatestAnswerPerReservedKey() {
        UUID gatheringId = randomTable.getId();
        Map<ReservedQuestionKey, String> formKeys = new EnumMap<>(ReservedQuestionKey.class);
        formKeys.put(ReservedQuestionKey.GENDER, "gender");
        formKeys.put(ReservedQuestionKey.MBTI, "mbti");
        formKeys.put(ReservedQuestionKey.BIRTH_YEAR, "birth_year");
        given(formService.findReservedQuestionKeys(gatheringId)).willReturn(formKeys);
        // 최신순. 예전 폼의 질문 키가 달라도 reservedKey로 대응한다. 생년은 답한 적 없다.
        given(applicationAnswerRepository.findReservedAnswersByUserId(userId)).willReturn(List.of(
                answer(ReservedQuestionKey.MBTI, "old_mbti_key", "ENFP"),
                answer(ReservedQuestionKey.GENDER, "gender", "FEMALE"),
                answer(ReservedQuestionKey.MBTI, "old_mbti_key", "ISTJ")));

        DiningPrefillResponse response = diningApplicationService.getPrefill(userId, gatheringId);

        assertThat(response.getAnswers())
                .extracting(DiningPrefillResponse.Answer::getReservedKey,
                        DiningPrefillResponse.Answer::getQuestionKey,
                        DiningPrefillResponse.Answer::getValue)
                .containsExactly(
                        tuple(ReservedQuestionKey.GENDER, "gender", "FEMALE"),
                        tuple(ReservedQuestionKey.MBTI, "mbti", "ENFP"));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    // 오늘 기준 days일 뒤(음수면 전) 회차. 시작 시간이 없으니 그날 0시 시작.
    private GatheringSession sessionOn(int days) {
        GatheringSession session = GatheringSession.builder()
                .gathering(randomTable)
                .eventDate(LocalDate.now().plusDays(days))
                .maxAttendees(6)
                .build();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    // 이용권 차감으로 확정된 신청. assigned가 null이면 매칭 전.
    private Application confirmedApplication(GatheringSession assigned) {
        Application application = Application.builder()
                .bookingNumber("WH260930-ABC123")
                .gathering(randomTable)
                .session(assigned)
                .user(user)
                .name(user.getName())
                .phone(user.getPhone())
                .build();
        ReflectionTestUtils.setField(application, "id", applicationId);
        application.confirm();
        return application;
    }

    private ApplicationCandidateSession candidate(Application application, GatheringSession session, int priority) {
        return new ApplicationCandidateSession(application, session, priority);
    }

    private ApplicationAnswer answer(ReservedQuestionKey reservedKey, String questionKey, Object value) {
        FormQuestion question = FormQuestion.builder()
                .questionKey(questionKey)
                .type(QuestionType.SINGLE_CHOICE)
                .label(questionKey)
                .reservedKey(reservedKey)
                .build();
        return ApplicationAnswer.builder().question(question).value(Map.of("value", value)).build();
    }
}
