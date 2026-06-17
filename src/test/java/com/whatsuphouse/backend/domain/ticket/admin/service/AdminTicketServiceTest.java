package com.whatsuphouse.backend.domain.ticket.admin.service;

import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminTicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminTicketServiceTest {

    @Mock private TicketPassRepository ticketPassRepository;

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
        return TicketPass.builder().user(user).product(TicketProduct.RANDOM_TABLE_FOUR).build();
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
}
