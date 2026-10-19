package com.whatsuphouse.backend.domain.ticket.service;

import com.whatsuphouse.backend.domain.application.client.service.MembershipWeekRule;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringSessionService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProductKind;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketProductRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/** 기간제 멤버십 이용권(KAN-401): 기간 계산, 차감 우선순위, 복원, 매칭 실패 연장, 만료. */
@ExtendWith(MockitoExtension.class)
class TicketServiceMembershipTest {

    @Mock private TicketPassRepository ticketPassRepository;
    @Mock private UserRepository userRepository;
    @Mock private TicketTransactionRepository ticketTransactionRepository;
    @Mock private TicketProductRepository ticketProductRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private GatheringSessionService gatheringSessionService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DepositService depositService;
    @Mock private MembershipWeekRule membershipWeekRule;
    @Mock private ExceptionCaseService exceptionCaseService;

    @InjectMocks private TicketService ticketService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("t@example.com").password("p").name("홍길동")
                .gender(Gender.MALE).age(25).nickname("nick").phone("01012345678")
                .build();
        userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    private TicketPass membership(LocalDateTime activatedAt, int months) {
        TicketPass pass = new TicketPass(user, null, new TicketProductOption(
                "멤버십 " + months + "개월", 0, 35000, TicketProductKind.PERIOD, months));
        if (activatedAt != null) {
            pass.activate(activatedAt);
        }
        return pass;
    }

    private TicketPass countPass(int count) {
        TicketPass pass = new TicketPass(user, null, new TicketProductOption("1회권", count, 9900));
        pass.activate();
        return pass;
    }

    private Application applicationWithId() {
        Application application = mock(Application.class);
        given(application.getId()).willReturn(UUID.randomUUID());
        return application;
    }

    private void givenMemberships(TicketPass... passes) {
        given(ticketPassRepository.findMembershipsByUserForUpdate(userId, TicketPassStatus.ACTIVE, TicketProductKind.PERIOD))
                .willReturn(List.of(passes));
    }

    @Test
    @DisplayName("멤버십 입금 확인: 활성화 날짜 + 1개월이 만료일, 결제 대기 신청은 수량 0 USE로 확정하고 잔여는 건드리지 않는다")
    void settlePass_period_setsValidUntil() {
        Gathering randomTable = Gathering.builder().title("우연한 식탁")
                .gatheringType(GatheringType.RANDOM_TABLE).build();
        Application application = Application.builder().bookingNumber("WH-M-001").gathering(randomTable)
                .user(user).name("홍길동").phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.awaitPayment();
        TicketPass pass = new TicketPass(user, application, new TicketProductOption(
                "멤버십 1개월", 0, 35000, TicketProductKind.PERIOD, 1));
        UUID passId = UUID.randomUUID();
        ReflectionTestUtils.setField(pass, "id", passId);
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)).willReturn(Optional.of(pass));
        given(depositService.isSatisfied(application.getId())).willReturn(true);

        ticketService.settlePass(passId, "WEBHOOK");

        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(pass.getValidUntil()).isEqualTo(pass.getActivatedAt().toLocalDate().plusMonths(1));
        assertThat(pass.getRemainingCount()).isZero();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        ArgumentCaptor<TicketTransaction> saved = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should(times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(TicketTransaction::getTransactionType)
                .containsExactly(TicketTransactionType.ISSUE, TicketTransactionType.USE);
        assertThat(saved.getAllValues()).extracting(TicketTransaction::getQuantity).containsExactly(0, 0);
        assertThat(saved.getAllValues().get(1).getStatus()).isEqualTo(TicketDeductionStatus.DEDUCTED);
    }

    @Test
    @DisplayName("멤버십과 횟수권이 모두 있으면 멤버십을 쓰고(USE 수량 0, DEDUCTED) 횟수권 잔여는 그대로")
    void tryUseOneTicket_prefersMembershipOverCount() {
        LocalDate sessionDate = LocalDate.now().plusDays(3);
        TicketPass member = membership(LocalDateTime.now(), 1);
        TicketPass count = countPass(1);
        givenMemberships(member);
        Application application = applicationWithId();

        assertThat(ticketService.tryUseOneTicket(user, application, sessionDate)).isTrue();

        assertThat(count.getRemainingCount()).isEqualTo(1);
        then(ticketPassRepository).should(never()).findUsableByUserForUpdate(any(), any(), any());
        ArgumentCaptor<TicketTransaction> saved = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should().save(saved.capture());
        assertThat(saved.getValue().getTicketPass()).isSameAs(member);
        assertThat(saved.getValue().getTransactionType()).isEqualTo(TicketTransactionType.USE);
        assertThat(saved.getValue().getQuantity()).isZero();
        assertThat(saved.getValue().getStatus()).isEqualTo(TicketDeductionStatus.DEDUCTED);
    }

    @Test
    @DisplayName("회차 날짜가 멤버십 만료일 뒤면 멤버십을 쓰지 않고 횟수권으로, 횟수권도 없으면 false")
    void tryUseOneTicket_membershipExpiredForSession_fallsBackOrFalse() {
        TicketPass member = membership(LocalDateTime.of(2026, 10, 20, 10, 0), 1);   // ~2026-11-20
        LocalDate afterExpiry = LocalDate.of(2026, 11, 21);
        TicketPass count = countPass(2);
        givenMemberships(member);
        given(ticketPassRepository.findUsableByUserForUpdate(eq(userId), eq(TicketPassStatus.ACTIVE), any(Pageable.class)))
                .willReturn(List.of(count), List.of());

        assertThat(ticketService.tryUseOneTicket(user, applicationWithId(), afterExpiry)).isTrue();
        assertThat(count.getRemainingCount()).isEqualTo(1);
        assertThat(ticketService.hasMembershipFor(userId, afterExpiry)).isFalse();
        assertThat(ticketService.hasMembershipFor(userId, LocalDate.of(2026, 11, 20))).isTrue();

        assertThat(ticketService.tryUseOneTicket(user, applicationWithId(), afterExpiry)).isFalse();
    }

    @Test
    @DisplayName("멤버십으로 확정한 신청을 취소 복원해도 remainingCount는 늘지 않고 REFUND 수량은 0")
    void refundOneTicket_periodPass_remainingUnchanged() {
        TicketPass member = membership(LocalDateTime.now(), 1);
        Application application = applicationWithId();
        TicketTransaction use = TicketTransaction.of(member, application, TicketTransactionType.USE, 0, "멤버십");
        given(ticketTransactionRepository.findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(
                application.getId(), TicketTransactionType.USE)).willReturn(Optional.of(use));

        ticketService.refundOneTicket(application);

        assertThat(member.getRemainingCount()).isZero();
        assertThat(member.getTotalCount()).isZero();
        assertThat(use.getStatus()).isEqualTo(TicketDeductionStatus.RESTORED);
        ArgumentCaptor<TicketTransaction> saved = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should().save(saved.capture());
        assertThat(saved.getValue().getTransactionType()).isEqualTo(TicketTransactionType.REFUND);
        assertThat(saved.getValue().getQuantity()).isZero();
    }

    @Test
    @DisplayName("매칭 실패 연장은 신청당 한 번만 +7일, EXTEND 거래 1건")
    void extendMembershipForFailure_once() {
        TicketPass member = membership(LocalDateTime.of(2026, 10, 20, 10, 0), 1);
        Application application = applicationWithId();
        UUID applicationId = application.getId();
        TicketTransaction use = TicketTransaction.of(member, application, TicketTransactionType.USE, 0, "멤버십");
        given(ticketTransactionRepository.existsByApplication_IdAndTransactionType(applicationId, TicketTransactionType.EXTEND))
                .willReturn(false, true);
        given(ticketTransactionRepository.findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(
                applicationId, TicketTransactionType.USE)).willReturn(Optional.of(use));

        ticketService.extendMembershipForFailure(applicationId);
        ticketService.extendMembershipForFailure(applicationId);

        assertThat(member.getValidUntil()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(member.getExtendedDays()).isEqualTo(7);
        ArgumentCaptor<TicketTransaction> saved = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should(times(1)).save(saved.capture());
        assertThat(saved.getValue().getTransactionType()).isEqualTo(TicketTransactionType.EXTEND);
        assertThat(saved.getValue().getQuantity()).isZero();
    }

    @Test
    @DisplayName("횟수권으로 확정한 신청은 매칭 실패해도 연장하지 않는다")
    void extendMembershipForFailure_countPass_ignored() {
        TicketPass count = countPass(2);
        Application application = applicationWithId();
        UUID applicationId = application.getId();
        TicketTransaction use = TicketTransaction.of(count, application, TicketTransactionType.USE, -1, "횟수권");
        given(ticketTransactionRepository.findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(
                applicationId, TicketTransactionType.USE)).willReturn(Optional.of(use));

        ticketService.extendMembershipForFailure(applicationId);

        then(ticketTransactionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("만료일이 지난 멤버십은 USED_UP + EXPIRE 거래(수량 0)")
    void expireMemberships_marksUsedUp() {
        TicketPass member = membership(LocalDateTime.of(2026, 9, 1, 10, 0), 1);   // ~2026-10-01
        LocalDate today = LocalDate.of(2026, 10, 2);
        given(ticketPassRepository.findByStatusAndProductOption_KindAndValidUntilBeforeAndDeletedAtIsNull(
                TicketPassStatus.ACTIVE, TicketProductKind.PERIOD, today)).willReturn(List.of(member));

        assertThat(ticketService.expireMemberships(today)).isEqualTo(1);

        assertThat(member.getStatus()).isEqualTo(TicketPassStatus.USED_UP);
        ArgumentCaptor<TicketTransaction> saved = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should().save(saved.capture());
        assertThat(saved.getValue().getTransactionType()).isEqualTo(TicketTransactionType.EXPIRE);
        assertThat(saved.getValue().getQuantity()).isZero();
    }
}
