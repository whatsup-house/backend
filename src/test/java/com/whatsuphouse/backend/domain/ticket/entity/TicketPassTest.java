package com.whatsuphouse.backend.domain.ticket.entity;

import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketPassTest {

    private TicketPass newPass() {
        User user = User.builder()
                .email("t@example.com").password("p").name("홍길동")
                .gender(Gender.MALE).age(25).nickname("nick").phone("01012345678")
                .build();
        return new TicketPass(user, null, new TicketProductOption("우연한 식탁 4회권", 4, 18000));
    }

    @Test
    @DisplayName("구매 직후엔 PENDING이고 잔여 0, 총 회차는 상품 회차")
    void newPass_isPendingWithZeroRemaining() {
        TicketPass pass = newPass();
        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.PENDING);
        assertThat(pass.getRemainingCount()).isZero();
        assertThat(pass.getTotalCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("1회권은 총 1회와 8천원 구매 금액을 스냅샷으로 가진다")
    void oneSessionPass_hasOneSessionPolicy() {
        User user = User.builder().email("one@example.com").password("p").name("회원")
                .gender(Gender.FEMALE).age(25).nickname("one").phone("01012345678").build();
        TicketPass pass = new TicketPass(user, null,
                new TicketProductOption("우연한 식탁 1회권", 1, 8000));

        assertThat(pass.getTotalCount()).isEqualTo(1);
        assertThat(pass.getPurchaseAmount()).isEqualTo(8000);
        assertThat(pass.getPaymentDeadline()).isNotNull();
    }

    @Test
    @DisplayName("활성화하면 ACTIVE가 되고 잔여가 총 회차로 충전된다")
    void activate_chargesRemaining() {
        TicketPass pass = newPass();
        pass.activate();
        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(pass.getRemainingCount()).isEqualTo(4);
        assertThat(pass.getActivatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 활성화된 이용권을 다시 활성화하면 예외")
    void activate_twice_throws() {
        TicketPass pass = newPass();
        pass.activate();
        assertThatThrownBy(pass::activate)
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("차감하면 잔여가 줄고, 0이 되면 USED_UP")
    void deductOne_decrementsAndUsedUp() {
        TicketPass pass = newPass();
        pass.activate();
        for (int i = 0; i < 4; i++) {
            pass.deductOne();
        }
        assertThat(pass.getRemainingCount()).isZero();
        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.USED_UP);
    }

    @Test
    @DisplayName("잔여가 없으면 차감 시 예외")
    void deductOne_whenEmpty_throws() {
        TicketPass pass = newPass();   // PENDING, remaining 0
        assertThatThrownBy(pass::deductOne)
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_AVAILABLE_TICKET);
    }

    @Test
    @DisplayName("환불하면 잔여가 늘고 USED_UP은 ACTIVE로 복구된다")
    void refundOne_restores() {
        TicketPass pass = newPass();
        pass.activate();
        for (int i = 0; i < 4; i++) {
            pass.deductOne();   // USED_UP
        }
        pass.refundOne();
        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(pass.getRemainingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("환불은 총 회차를 초과하지 않는다")
    void refundOne_capsAtTotal() {
        TicketPass pass = newPass();
        pass.activate();    // remaining 4 == total
        pass.refundOne();
        assertThat(pass.getRemainingCount()).isEqualTo(4);
    }
}
