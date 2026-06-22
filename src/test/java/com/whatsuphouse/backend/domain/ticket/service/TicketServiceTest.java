package com.whatsuphouse.backend.domain.ticket.service;

import com.whatsuphouse.backend.domain.ticket.dto.response.MyTicketsResponse;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketPassResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.participant.service.ParticipantService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketPassRepository ticketPassRepository;
    @Mock private UserRepository userRepository;
    @Mock private ParticipantService participantService;
    @Mock private TicketTransactionRepository ticketTransactionRepository;
    @Mock private ApplicationRepository applicationRepository;

    @InjectMocks private TicketService ticketService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .email("t@example.com").password("p").name("홍길동")
                .gender(Gender.MALE).age(25).nickname("nick").phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    private TicketPass activePass(int remaining) {
        TicketPass pass = TicketPass.builder().participant(Participant.member(user)).product(TicketProduct.RANDOM_TABLE_FOUR).build();
        pass.activate();
        int toDeduct = pass.getTotalCount() - remaining;
        for (int i = 0; i < toDeduct; i++) {
            pass.deductOne();
        }
        return pass;
    }

    @Test
    @DisplayName("구매하면 PENDING 이용권이 저장된다")
    void purchase_createsPendingPass() {
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        Participant participant = Participant.member(user);
        participant.approveRandomTable();
        given(participantService.getOrCreateForUser(any())).willReturn(participant);

        TicketPassResponse response = ticketService.purchase(userId, TicketProduct.RANDOM_TABLE_FOUR);

        then(ticketPassRepository).should().save(any(TicketPass.class));
        assertThat(response.getStatus()).isEqualTo(TicketPassStatus.PENDING);
        assertThat(response.getRemainingCount()).isZero();
        assertThat(response.getTotalCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("존재하지 않는 회원이 구매하면 예외")
    void purchase_userNotFound_throws() {
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.purchase(userId, TicketProduct.RANDOM_TABLE_FOUR))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("심사 승인 전에는 이용권을 구매할 수 없다")
    void purchase_unreviewedParticipant_throws() {
        Participant participant = Participant.member(user);
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(participantService.getOrCreateForUser(user)).willReturn(participant);

        assertThatThrownBy(() -> ticketService.purchase(userId, TicketProduct.RANDOM_TABLE_ONE))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_PURCHASE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("승인된 비회원은 예약번호로 이용권을 구매할 수 있다")
    void purchaseGuest_approvedApplication_createsPendingPass() {
        Participant guest = Participant.guest("비회원", "guest@test.com", "01012345678");
        ReflectionTestUtils.setField(guest, "id", UUID.randomUUID());
        guest.approveRandomTable();
        Application application = mock(Application.class);
        given(application.getParticipant()).willReturn(guest);
        given(application.getStatus()).willReturn(ApplicationStatus.PAYMENT_PENDING);
        given(applicationRepository.findByBookingNumberAndDeletedAtIsNull("WH260623-ABC123"))
                .willReturn(Optional.of(application));

        TicketPassResponse response = ticketService.purchaseGuest(
                "WH260623-ABC123", TicketProduct.RANDOM_TABLE_ONE);

        then(ticketPassRepository).should().save(any(TicketPass.class));
        assertThat(response.getStatus()).isEqualTo(TicketPassStatus.PENDING);
    }

    @Test
    @DisplayName("승인되지 않은 비회원 예약번호로는 이용권을 구매할 수 없다")
    void purchaseGuest_unreviewed_throws() {
        Participant guest = Participant.guest("비회원", "guest@test.com", "01012345678");
        Application application = mock(Application.class);
        given(application.getParticipant()).willReturn(guest);
        given(application.getStatus()).willReturn(ApplicationStatus.PENDING);
        given(applicationRepository.findByBookingNumberAndDeletedAtIsNull("WH260623-ABC123"))
                .willReturn(Optional.of(application));

        assertThatThrownBy(() -> ticketService.purchaseGuest(
                "WH260623-ABC123", TicketProduct.RANDOM_TABLE_ONE))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_PURCHASE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("내 이용권 조회 시 ACTIVE 잔여만 합산한다")
    void getMyTickets_sumsActiveRemaining() {
        TicketPass active = activePass(3);
        TicketPass usedUp = activePass(0);   // USED_UP
        Participant participant = active.getParticipant();
        participant.approveRandomTable();
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(participantService.getOrCreateForUser(user)).willReturn(participant);
        given(ticketPassRepository.findByParticipant_User_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId))
                .willReturn(List.of(active, usedUp));

        MyTicketsResponse response = ticketService.getMyTickets(userId);

        assertThat(response.getTotalRemaining()).isEqualTo(3);
        assertThat(response.getPasses()).hasSize(2);
        assertThat(response.isPurchasable()).isTrue();
        assertThat(response.getRandomTableEligibility()).isEqualTo(participant.getRandomTableEligibility());
    }

    @Test
    @DisplayName("우연한 식탁 신청 시 사용 가능한 이용권을 1회 차감한다")
    void useOneTicket_deducts() {
        TicketPass active = activePass(2);
        Participant participant = active.getParticipant();
        ReflectionTestUtils.setField(participant, "id", UUID.randomUUID());
        given(participantService.getOrCreateForUser(user)).willReturn(participant);
        given(ticketPassRepository.findUsableByParticipantForUpdate(eq(participant.getId()), eq(TicketPassStatus.ACTIVE), any(Pageable.class)))
                .willReturn(List.of(active));

        ticketService.useOneTicket(user);

        assertThat(active.getRemainingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("사용 가능한 이용권이 없으면 신청 차단 예외")
    void useOneTicket_whenNone_throws() {
        Participant participant = Participant.member(user);
        ReflectionTestUtils.setField(participant, "id", UUID.randomUUID());
        given(participantService.getOrCreateForUser(user)).willReturn(participant);
        given(ticketPassRepository.findUsableByParticipantForUpdate(eq(participant.getId()), eq(TicketPassStatus.ACTIVE), any(Pageable.class)))
                .willReturn(List.of());

        assertThatThrownBy(() -> ticketService.useOneTicket(user))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_AVAILABLE_TICKET);
    }

    @Test
    @DisplayName("취소 시 USED_UP 이용권을 환불해 ACTIVE로 복구한다")
    void refundOneTicket_restoresUsedUp() {
        TicketPass usedUp = activePass(0);   // USED_UP, remaining 0
        given(ticketPassRepository.findByParticipant_User_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId))
                .willReturn(List.of(usedUp));

        ticketService.refundOneTicket(user);

        assertThat(usedUp.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(usedUp.getRemainingCount()).isEqualTo(1);
    }
}
