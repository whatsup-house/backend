package com.whatsuphouse.backend.domain.application.enums;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static com.whatsuphouse.backend.domain.application.enums.DiningPaymentStatus.PAID;
import static com.whatsuphouse.backend.domain.application.enums.DiningPaymentStatus.PENDING;
import static com.whatsuphouse.backend.domain.application.enums.DiningPaymentStatus.REFUNDED;
import static com.whatsuphouse.backend.domain.application.enums.DiningPaymentStatus.REFUND_AVAILABLE;
import static com.whatsuphouse.backend.domain.application.enums.DiningPaymentStatus.REFUND_FAILED;
import static com.whatsuphouse.backend.domain.application.enums.DiningPaymentStatus.REFUND_PROCESSING;
import static com.whatsuphouse.backend.domain.application.enums.DiningPaymentStatus.REFUND_REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;

/** 통합 결제 상태 매핑 표(KAN-437). 신청 상태 × 이용권 차감 × 보증금 × 해결 선택 → paymentStatus. */
class DiningPaymentStatusTest {

    private static final Gathering RANDOM_TABLE = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();

    private static Application application(Gathering gathering, Consumer<Application> change) {
        Application application = Application.builder().bookingNumber("WH-1").gathering(gathering)
                .name("회원").phone("01012345678").build();
        change.accept(application);
        return application;
    }

    private static Application diningApplication(Consumer<Application> change) {
        return application(RANDOM_TABLE, change);
    }

    private static DiningPaymentStatus of(Application application, TicketDeductionStatus ticket, DepositStatus deposit,
                                          boolean resolutionOffered) {
        return DiningPaymentStatus.of(application, ticket, deposit, resolutionOffered);
    }

    @Test
    @DisplayName("우연한 식탁 진행 중 신청: 결제 대기·결제 완료·매칭 실패 환불 선택 가능")
    void of_activeApplication_mapping() {
        // given
        Application pendingApproval = diningApplication(a -> { });
        Application paymentPending = diningApplication(Application::awaitPayment);
        Application confirmed = diningApplication(Application::confirm);
        Application attended = diningApplication(a -> { a.confirm(); a.attend(); });
        Application offered = diningApplication(a -> { a.confirm(); a.changeMatchStatus(MatchStatus.ALTERNATIVE_OFFERED); });
        Application noMatch = diningApplication(a -> { a.confirm(); a.changeMatchStatus(MatchStatus.NO_MATCH); });

        // when & then
        assertThat(of(pendingApproval, null, null, false)).as("승인 대기").isEqualTo(PENDING);
        assertThat(of(paymentPending, null, null, false)).as("이용권 결제 대기").isEqualTo(PENDING);
        assertThat(of(paymentPending, TicketDeductionStatus.DEDUCTED, DepositStatus.PENDING, false))
                .as("이용권은 썼지만 보증금 입금 대기").isEqualTo(PENDING);
        assertThat(of(confirmed, TicketDeductionStatus.DEDUCTED, null, false)).as("이용권 차감 확정").isEqualTo(PAID);
        assertThat(of(attended, TicketDeductionStatus.DEDUCTED, DepositStatus.RETURN_PENDING, false))
                .as("체크인 뒤 보증금 반환은 환불이 아니다").isEqualTo(PAID);
        assertThat(of(offered, TicketDeductionStatus.DEDUCTED, null, true)).as("대체안 응답 대기").isEqualTo(REFUND_AVAILABLE);
        assertThat(of(noMatch, TicketDeductionStatus.DEDUCTED, null, true)).as("대체 회차 없음 응답 대기").isEqualTo(REFUND_AVAILABLE);
        assertThat(of(offered, TicketDeductionStatus.DEDUCTED, null, false)).as("응답 대기 제안이 없으면").isEqualTo(PAID);
        assertThat(of(confirmed, TicketDeductionStatus.RESTORED, DepositStatus.PAID, false))
                .as("회차 취소로 이용권 복원").isEqualTo(REFUNDED);
    }

    @Test
    @DisplayName("환불 진행 단계는 이용권 차감 상태를 그대로, 끝난 신청의 보증금 반환도 환불로 본다")
    void of_refundFlow_mapping() {
        // given
        Application cancelled = diningApplication(Application::cancel);

        // when & then
        assertThat(of(cancelled, TicketDeductionStatus.REFUND_REQUESTED, null, false)).isEqualTo(REFUND_REQUESTED);
        assertThat(of(cancelled, TicketDeductionStatus.REFUND_PROCESSING, DepositStatus.RETURN_PENDING, false))
                .isEqualTo(REFUND_PROCESSING);
        assertThat(of(cancelled, TicketDeductionStatus.REFUNDED, null, false)).isEqualTo(REFUNDED);
        assertThat(of(cancelled, TicketDeductionStatus.REFUND_FAILED, DepositStatus.RETURN_PENDING, false))
                .as("실패가 가장 우선").isEqualTo(REFUND_FAILED);
        assertThat(of(cancelled, TicketDeductionStatus.RESTORED, DepositStatus.RETURN_PENDING, false))
                .as("이용권 보관 + 보증금 반환 대기").isEqualTo(REFUND_PROCESSING);
        assertThat(of(cancelled, TicketDeductionStatus.RESTORED, DepositStatus.RETURNED, false)).isEqualTo(REFUNDED);
        assertThat(of(cancelled, TicketDeductionStatus.DEDUCTED, DepositStatus.RETURNED, false))
                .as("멤버십 취소 + 보증금 반환 완료").isEqualTo(REFUNDED);
    }

    @Test
    @DisplayName("끝난 신청: 환불 없이 끝났으면 PAID, 결제 전에 끝났으면 결제 상태 없음(null)")
    void of_closedApplication_mapping() {
        // given
        Application cancelled = diningApplication(Application::cancel);
        Application rejected = diningApplication(a -> a.reject("자격 미달"));

        // when & then
        assertThat(of(cancelled, TicketDeductionStatus.DEDUCTED, DepositStatus.FORFEITED, false))
                .as("당일 취소: 환불 없음·보증금 몰수").isEqualTo(PAID);
        assertThat(of(cancelled, null, null, false)).as("결제 전 취소").isNull();
        assertThat(of(cancelled, null, DepositStatus.PENDING, false)).as("보증금도 미입금").isNull();
        assertThat(of(rejected, null, null, false)).as("결제 전 반려").isNull();
    }

    @Test
    @DisplayName("일반 모임: 유료면 입금 확인 여부, 무료면 null")
    void of_regularGathering_mapping() {
        // given
        Gathering paid = Gathering.builder().title("퇴근 게더링").gatheringType(GatheringType.REGULAR).basePrice(30000).build();
        Gathering free = Gathering.builder().title("무료 모임").gatheringType(GatheringType.REGULAR).basePrice(0).build();

        // when & then
        assertThat(of(application(paid, a -> { }), null, null, false)).isEqualTo(PENDING);
        assertThat(of(application(paid, Application::confirmPayment), null, null, false)).isEqualTo(PAID);
        assertThat(of(application(free, a -> { }), null, null, false)).isNull();
    }
}
