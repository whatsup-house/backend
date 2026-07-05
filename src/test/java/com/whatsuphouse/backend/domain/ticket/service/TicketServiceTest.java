package com.whatsuphouse.backend.domain.ticket.service;

import com.whatsuphouse.backend.domain.ticket.dto.response.MyTicketsResponse;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketPassResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.notification.event.TicketPurchaseRequestedEvent;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketProductRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketPassRepository ticketPassRepository;
    @Mock private UserRepository userRepository;
    @Mock private ParticipantService participantService;
    @Mock private TicketTransactionRepository ticketTransactionRepository;
    @Mock private TicketProductRepository ticketProductRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TicketService ticketService;

    private UUID userId;
    private User user;
    private UUID productId;
    private TicketProductOption fourSessionProduct;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .email("t@example.com").password("p").name("홍길동")
                .gender(Gender.MALE).age(25).nickname("nick").phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        productId = UUID.randomUUID();
        fourSessionProduct = new TicketProductOption("우연한 식탁 4회권", 4, 18000);
        ReflectionTestUtils.setField(fourSessionProduct, "id", productId);
    }

    private TicketPass activePass(int remaining) {
        TicketPass pass = new TicketPass(Participant.member(user), null, fourSessionProduct);
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
        given(ticketProductRepository.findByIdAndDeletedAtIsNull(productId))
                .willReturn(Optional.of(fourSessionProduct));

        TicketPassResponse response = ticketService.purchase(userId, productId, null);

        then(ticketPassRepository).should().save(any(TicketPass.class));
        assertThat(response.getStatus()).isEqualTo(TicketPassStatus.PENDING);
        assertThat(response.getRemainingCount()).isZero();
        assertThat(response.getTotalCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("상품 ID 없이 구매하면 예외")
    void purchase_withoutProductId_throws() {
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        Participant participant = Participant.member(user);
        participant.approveRandomTable();
        given(participantService.getOrCreateForUser(any())).willReturn(participant);

        assertThatThrownBy(() -> ticketService.purchase(userId, null, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 회원이 구매하면 예외")
    void purchase_userNotFound_throws() {
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.purchase(userId, productId, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("심사 승인 전에는 이용권을 구매할 수 없다")
    void purchase_unreviewedParticipant_throws() {
        Participant participant = Participant.member(user);
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(participantService.getOrCreateForUser(user)).willReturn(participant);

        assertThatThrownBy(() -> ticketService.purchase(userId, productId, null))
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
        Gathering gathering = mock(Gathering.class);
        given(application.getParticipant()).willReturn(guest);
        given(application.getGathering()).willReturn(gathering);
        given(gathering.getGatheringType()).willReturn(GatheringType.RANDOM_TABLE);
        given(application.getStatus()).willReturn(ApplicationStatus.PAYMENT_PENDING);
        given(applicationRepository.findByBookingNumberAndDeletedAtIsNull("WH260623-ABC123"))
                .willReturn(Optional.of(application));
        given(ticketProductRepository.findByIdAndDeletedAtIsNull(productId))
                .willReturn(Optional.of(fourSessionProduct));

        TicketPassResponse response = ticketService.purchaseGuest("WH260623-ABC123", productId);

        then(ticketPassRepository).should().save(any(TicketPass.class));
        then(eventPublisher).should().publishEvent(any(TicketPurchaseRequestedEvent.class));
        assertThat(response.getStatus()).isEqualTo(TicketPassStatus.PENDING);
    }

    @Test
    @DisplayName("승인되지 않은 비회원 예약번호로는 이용권을 구매할 수 없다")
    void purchaseGuest_unreviewed_throws() {
        Participant guest = Participant.guest("비회원", "guest@test.com", "01012345678");
        Application application = mock(Application.class);
        Gathering gathering = mock(Gathering.class);
        given(application.getParticipant()).willReturn(guest);
        given(application.getGathering()).willReturn(gathering);
        given(gathering.getGatheringType()).willReturn(GatheringType.RANDOM_TABLE);
        given(applicationRepository.findByBookingNumberAndDeletedAtIsNull("WH260623-ABC123"))
                .willReturn(Optional.of(application));

        assertThatThrownBy(() -> ticketService.purchaseGuest("WH260623-ABC123", productId))
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
    @DisplayName("우연한 식탁 신청 시 사용 가능한 이용권을 1회 차감하고 USE 거래를 남긴다")
    void tryUseOneTicket_deductsAndRecords() {
        TicketPass active = activePass(2);
        Participant participant = active.getParticipant();
        ReflectionTestUtils.setField(participant, "id", UUID.randomUUID());
        given(ticketPassRepository.findUsableByParticipantForUpdate(eq(participant.getId()), eq(TicketPassStatus.ACTIVE), any(Pageable.class)))
                .willReturn(List.of(active));

        boolean used = ticketService.tryUseOneTicket(participant, null);

        assertThat(used).isTrue();
        assertThat(active.getRemainingCount()).isEqualTo(1);
        then(ticketTransactionRepository).should().save(any(TicketTransaction.class));
    }

    @Test
    @DisplayName("사용 가능한 이용권이 없으면 false를 반환하고 상태를 바꾸지 않는다")
    void tryUseOneTicket_whenNone_returnsFalse() {
        Participant participant = Participant.member(user);
        ReflectionTestUtils.setField(participant, "id", UUID.randomUUID());
        given(ticketPassRepository.findUsableByParticipantForUpdate(eq(participant.getId()), eq(TicketPassStatus.ACTIVE), any(Pageable.class)))
                .willReturn(List.of());

        assertThat(ticketService.tryUseOneTicket(participant, null)).isFalse();
        then(ticketTransactionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("취소 시 USE 거래를 기준으로 환불하고 REFUND 거래를 남긴다")
    void refundOneTicket_restoresByLedger() {
        TicketPass usedUp = activePass(0);   // USED_UP, remaining 0
        UUID applicationId = UUID.randomUUID();
        Application application = mock(Application.class);
        given(application.getId()).willReturn(applicationId);
        given(ticketTransactionRepository.existsByApplication_IdAndTransactionType(applicationId, TicketTransactionType.REFUND))
                .willReturn(false);
        given(ticketTransactionRepository.findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(applicationId, TicketTransactionType.USE))
                .willReturn(Optional.of(TicketTransaction.of(usedUp, application, TicketTransactionType.USE, -1, "테스트")));

        ticketService.refundOneTicket(application);

        assertThat(usedUp.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(usedUp.getRemainingCount()).isEqualTo(1);
        ArgumentCaptor<TicketTransaction> captor = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should().save(captor.capture());
        assertThat(captor.getValue().getTransactionType()).isEqualTo(TicketTransactionType.REFUND);
    }

    @Test
    @DisplayName("[불변식] 모든 잔액 변경은 거래 합계와 일치한다 (sum(quantity) == remaining - total)")
    void ledgerInvariant_holdsAcrossDeductions() {
        TicketPass active = activePass(4);
        Participant participant = active.getParticipant();
        ReflectionTestUtils.setField(participant, "id", UUID.randomUUID());
        given(ticketPassRepository.findUsableByParticipantForUpdate(eq(participant.getId()), eq(TicketPassStatus.ACTIVE), any(Pageable.class)))
                .willReturn(List.of(active));

        ticketService.tryUseOneTicket(participant, null);
        ticketService.tryUseOneTicket(participant, null);

        ArgumentCaptor<TicketTransaction> captor = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should(atLeastOnce()).save(captor.capture());
        int ledgerSum = captor.getAllValues().stream().mapToInt(TicketTransaction::getQuantity).sum();
        assertThat(ledgerSum).isEqualTo(active.getRemainingCount() - active.getTotalCount());
    }
}
