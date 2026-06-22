package com.whatsuphouse.backend.domain.ticket.admin.service;

import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminTicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.admin.dto.request.TicketAdjustmentRequest;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AdminTicketServiceTest {

    @Mock private TicketPassRepository ticketPassRepository;
    @Mock private TicketTransactionRepository ticketTransactionRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AdminTicketService adminTicketService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("t@example.com").password("p").name("홍길동")
                .gender(Gender.MALE).age(25).nickname("nick").phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    private TicketPass pendingPass() {
        return TicketPass.builder().participant(Participant.member(user)).product(TicketProduct.RANDOM_TABLE_FOUR).build();
    }

    @Test
    @DisplayName("입금 확인하면 이용권이 활성화된다")
    void confirm_activates() {
        UUID id = UUID.randomUUID();
        TicketPass pass = pendingPass();
        given(ticketPassRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(pass));

        AdminTicketPassResponse response = adminTicketService.confirm(id);

        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(response.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(response.getTotalCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("입금 확인 시 결제 대기 신청에 1회를 사용하고 자동 확정한다")
    void confirm_paymentPendingApplication_autoConfirms() {
        UUID id = UUID.randomUUID();
        TicketPass pass = pendingPass();
        Participant participant = pass.getParticipant();
        ReflectionTestUtils.setField(participant, "id", UUID.randomUUID());
        Gathering gathering = Gathering.builder().title("우연한 식탁")
                .eventDate(LocalDate.now().plusDays(7)).maxAttendees(4)
                .gatheringType(GatheringType.RANDOM_TABLE).build();
        ReflectionTestUtils.setField(gathering, "id", UUID.randomUUID());
        Application application = Application.builder().bookingNumber("WH-PAY-001")
                .gathering(gathering).participant(participant).name("홍길동")
                .phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.awaitPayment();

        given(ticketPassRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(pass));
        given(applicationRepository.findFirstByParticipant_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                participant.getId(), ApplicationStatus.PAYMENT_PENDING)).willReturn(Optional.of(application));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(
                gathering.getId(), ApplicationStatus.SEAT_OCCUPYING)).willReturn(0);

        adminTicketService.confirm(id);

        assertThat(pass.getRemainingCount()).isEqualTo(3);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        assertThat(application.isPaymentConfirmed()).isTrue();
        then(eventPublisher).should().publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("존재하지 않는 이용권 확인 시 예외")
    void confirm_notFound_throws() {
        UUID id = UUID.randomUUID();
        given(ticketPassRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminTicketService.confirm(id))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_PASS_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 활성화된 이용권을 다시 확인하면 예외")
    void confirm_alreadyActive_throws() {
        UUID id = UUID.randomUUID();
        TicketPass pass = pendingPass();
        pass.activate();
        given(ticketPassRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(pass));

        assertThatThrownBy(() -> adminTicketService.confirm(id))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("입금 대기 목록을 반환한다")
    void listPending_returnsMapped() {
        given(ticketPassRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(TicketPassStatus.PENDING))
                .willReturn(List.of(pendingPass()));

        List<AdminTicketPassResponse> result = adminTicketService.listPending();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(TicketPassStatus.PENDING);
    }

    @Test
    @DisplayName("관리자는 사유와 함께 이용권 횟수를 회수하고 복구할 수 있다")
    void adjust_changesRemaining() {
        UUID id = UUID.randomUUID();
        TicketPass pass = pendingPass();
        pass.activate();
        given(ticketPassRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(pass));

        adminTicketService.adjust(id, TicketAdjustmentRequest.builder()
                .quantity(-2).reason("노쇼 회수").build());
        assertThat(pass.getRemainingCount()).isEqualTo(2);

        adminTicketService.adjust(id, TicketAdjustmentRequest.builder()
                .quantity(1).reason("관리자 복구").build());
        assertThat(pass.getRemainingCount()).isEqualTo(3);
    }
}
