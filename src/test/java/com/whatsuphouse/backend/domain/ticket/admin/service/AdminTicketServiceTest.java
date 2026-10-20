package com.whatsuphouse.backend.domain.ticket.admin.service;

import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminPendingDepositResponse;
import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminTicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.admin.dto.request.TicketAdjustmentRequest;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminTicketServiceTest {

    @Mock private TicketPassRepository ticketPassRepository;
    @Mock private TicketTransactionRepository ticketTransactionRepository;
    @Mock private TicketService ticketService;

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
        return new TicketPass(user, null, new TicketProductOption("우연한 식탁 4회권", 4, 18000));
    }

    @Test
    @DisplayName("입금 확인은 TicketService.settlePass에 관리자 actor로 위임한다")
    void confirm_delegatesToSettlePass() {
        UUID id = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        TicketPass pass = pendingPass();
        pass.activate();
        given(ticketService.settlePass(id, "ADMIN:" + adminId)).willReturn(pass);

        AdminTicketPassResponse response = adminTicketService.confirm(id, adminId);

        assertThat(response.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(response.getTotalCount()).isEqualTo(4);
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

    @Test
    @DisplayName("입금 대기 큐는 연결된 신청자·게더링 정보를 붙여 반환한다")
    void listPendingDeposits_includesApplicationContext() {
        TicketPass pass = pendingPass();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        Gathering gathering = Gathering.builder().title("우연한 식탁")
                .gatheringType(GatheringType.RANDOM_TABLE).build();
        ReflectionTestUtils.setField(gathering, "id", UUID.randomUUID());
        GatheringSession session = GatheringSession.builder().gathering(gathering)
                .eventDate(LocalDate.now().plusDays(7)).maxAttendees(4).build();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        Application application = Application.builder().bookingNumber("WH-PAY-002")
                .session(session).user(user).name("홍길동")
                .phone("01012345678").build();
        application.awaitPayment();
        assertThat(application.getMatchStatus()).isEqualTo(MatchStatus.WAITING);

        given(ticketPassRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(TicketPassStatus.PENDING))
                .willReturn(List.of(pass));
        given(ticketService.resolvePaymentPendingApplication(pass)).willReturn(application);

        List<AdminPendingDepositResponse> result = adminTicketService.listPendingDeposits();

        assertThat(result).hasSize(1);
        AdminPendingDepositResponse row = result.get(0);
        assertThat(row.getApplicantName()).isEqualTo("홍길동");
        assertThat(row.isMember()).isTrue();
        assertThat(row.getProductLabel()).isEqualTo("우연한 식탁 4회권");
        assertThat(row.getAmount()).isEqualTo(18000);
        assertThat(row.getBookingNumber()).isEqualTo("WH-PAY-002");
        assertThat(row.getGatheringTitle()).isEqualTo("우연한 식탁");
    }

    @Test
    @DisplayName("입금 대기 건수를 반환한다")
    void countPendingDeposits_returnsCount() {
        given(ticketPassRepository.countByStatusAndDeletedAtIsNull(TicketPassStatus.PENDING)).willReturn(3L);

        assertThat(adminTicketService.countPendingDeposits()).isEqualTo(3L);
    }
}
