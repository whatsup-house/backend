package com.whatsuphouse.backend.domain.ticket.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import com.whatsuphouse.backend.domain.payment.enums.PayoutStatus;
import com.whatsuphouse.backend.domain.payment.enums.PayoutType;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.payment.service.PayoutService;
import com.whatsuphouse.backend.domain.ticket.dto.response.MyTicketsResponse;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProductKind;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

/** 멤버십 환불(KAN-404): 첫 참석 전까지 전액, 진행 중 신청 함께 취소, 내 이용권 화면 환불 상태. */
@ExtendWith(MockitoExtension.class)
class TicketServiceMembershipRefundTest {

    @Mock private TicketPassRepository ticketPassRepository;
    @Mock private UserRepository userRepository;
    @Mock private TicketTransactionRepository ticketTransactionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DepositService depositService;
    @Mock private PayoutService payoutService;
    @Mock private ObjectProvider<DiningAttendanceService> diningAttendanceServiceProvider;
    @Mock private DiningAttendanceService diningAttendanceService;

    @InjectMocks private TicketService ticketService;

    private final RefundAccount account = new RefundAccount("국민", "12345678901", "홍길동");
    private User user;
    private UUID userId;
    private TicketPass membership;

    @BeforeEach
    void setUp() {
        user = User.builder().email("t@example.com").password("p").name("홍길동")
                .gender(Gender.MALE).age(25).nickname("nick").phone("01012345678").build();
        userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);
        membership = new TicketPass(user, null, new TicketProductOption(
                "멤버십 1개월", 0, 35_000, TicketProductKind.PERIOD, 1));
        ReflectionTestUtils.setField(membership, "id", UUID.randomUUID());
        membership.activate(LocalDateTime.now().minusDays(3));
    }

    private void givenLockedMembership() {
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(membership.getId())).willReturn(Optional.of(membership));
        given(diningAttendanceServiceProvider.getObject()).willReturn(diningAttendanceService);
    }

    private Application confirmedApplicationUsing(TicketPass pass) {
        Gathering randomTable = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        Application application = Application.builder().bookingNumber("WH-MR-001").gathering(randomTable)
                .user(user).name("홍길동").phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.confirm();
        TicketTransaction use = TicketTransaction.of(pass, application, TicketTransactionType.USE, 0, "멤버십");
        lenient().when(ticketTransactionRepository.findByTicketPass_IdOrderByCreatedAtDesc(pass.getId())).thenReturn(List.of(use));
        lenient().when(ticketTransactionRepository.findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(
                application.getId(), TicketTransactionType.USE)).thenReturn(Optional.of(use));
        return application;
    }

    @Test
    @DisplayName("첫 참석 이후면 MEMBERSHIP_REFUND_AFTER_ATTENDANCE, 송금 요청을 만들지 않는다")
    void refundMembership_afterAttendance_throws() {
        givenLockedMembership();
        given(diningAttendanceService.hasAttendedSince(userId, membership.getActivatedAt())).willReturn(true);

        assertThatThrownBy(() -> ticketService.refundMembership(membership.getId(), userId, account))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBERSHIP_REFUND_AFTER_ATTENDANCE);
        then(ticketPassRepository).should().refreshForUpdate(membership);
        assertThat(membership.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        then(payoutService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("첫 참석 전이면 진행 중(테이블 전) 신청을 함께 취소하고 이용권 CANCELLED + 구매가 전액 송금 요청")
    void refundMembership_beforeAttendance_createsPayoutAndCancelsPass() {
        givenLockedMembership();
        Application application = confirmedApplicationUsing(membership);
        given(diningAttendanceService.hasAttendedSince(userId, membership.getActivatedAt())).willReturn(false);
        given(diningAttendanceService.findSeatedApplicationIds(Set.of(application.getId()))).willReturn(Set.of());

        ticketService.refundMembership(membership.getId(), userId, account);

        assertThat(membership.getStatus()).isEqualTo(TicketPassStatus.CANCELLED);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        then(depositService).should().requestReturn(application.getId());
        then(eventPublisher).should().publishEvent(any(ApplicationCancelledEvent.class));
        then(payoutService).should().request(PayoutType.REFUND, userId, 35_000, account, null, membership.getId(), null);
        ArgumentCaptor<TicketTransaction> saved = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should(org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(TicketTransaction::getTransactionType)
                .containsOnly(TicketTransactionType.REFUND);
        assertThat(saved.getAllValues()).extracting(TicketTransaction::getQuantity).containsOnly(0);
    }

    @Test
    @DisplayName("테이블(제안·확정)에 앉은 신청이 있으면 MEMBERSHIP_REFUND_ACTIVE_TABLE, 아무것도 취소하지 않는다")
    void refundMembership_activeTable_throws() {
        givenLockedMembership();
        Application application = confirmedApplicationUsing(membership);
        given(diningAttendanceService.hasAttendedSince(userId, membership.getActivatedAt())).willReturn(false);
        given(diningAttendanceService.findSeatedApplicationIds(Set.of(application.getId())))
                .willReturn(Set.of(application.getId()));

        assertThatThrownBy(() -> ticketService.refundMembership(membership.getId(), userId, account))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBERSHIP_REFUND_ACTIVE_TABLE);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        assertThat(membership.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        then(payoutService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("남의 이용권이면 TICKET_PASS_NOT_FOUND, 횟수권·비활성 이용권이면 MEMBERSHIP_REFUND_NOT_ALLOWED")
    void refundMembership_notOwnedOrNotMembership() {
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(membership.getId())).willReturn(Optional.of(membership));
        assertThatThrownBy(() -> ticketService.refundMembership(membership.getId(), UUID.randomUUID(), account))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_PASS_NOT_FOUND);

        membership.cancel();
        assertThatThrownBy(() -> ticketService.refundMembership(membership.getId(), userId, account))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBERSHIP_REFUND_NOT_ALLOWED);
    }

    @Test
    @DisplayName("내 이용권: 환불 요청한 CANCELLED 멤버십에 송금 상태(REQUESTED/SENT)를 싣고, 그 외 이용권은 null")
    void getMyTickets_cancelledMembershipHasRefundStatus() {
        TicketPass active = new TicketPass(user, null, new TicketProductOption("1회권", 1, 9_900));
        ReflectionTestUtils.setField(active, "id", UUID.randomUUID());
        membership.cancel();
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(ticketPassRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId))
                .willReturn(List.of(membership, active));
        given(payoutService.findRefundStatusesByPassIds(List.of(membership.getId())))
                .willReturn(Map.of(membership.getId(), PayoutStatus.REQUESTED));

        MyTicketsResponse response = ticketService.getMyTickets(userId);

        assertThat(response.getPasses().get(0).getRefundStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(response.getPasses().get(1).getRefundStatus()).isNull();
    }

    @Test
    @DisplayName("멤버십 매칭 실패 환불 선택은 돈으로 돌려주지 않는다(기간 연장으로 보상) → 빈 값")
    void refundPurchase_membershipUse_empty() {
        Application application = confirmedApplicationUsing(membership);

        assertThat(ticketService.refundPurchase(application, account)).isEmpty();
        then(payoutService).shouldHaveNoInteractions();
        assertThat(ticketTransactionRepository.findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(
                application.getId(), TicketTransactionType.USE).orElseThrow().getStatus())
                .isEqualTo(TicketDeductionStatus.DEDUCTED);
    }
}
