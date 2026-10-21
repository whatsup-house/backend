package com.whatsuphouse.backend.domain.application.enums;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;

/**
 * 통합 결제 상태(PRD 12장). 저장 컬럼이 아니라 신청 상태·이용권 차감·보증금·해결 선택에서 계산한다. (KAN-437)
 * 위에서부터 처음 맞는 것:
 * 1. 이용권 차감 REFUND_FAILED / REFUND_REQUESTED → 그대로
 * 2. 이용권 차감 REFUND_PROCESSING, 또는 끝난(취소·반려) 신청의 보증금 RETURN_PENDING → REFUND_PROCESSING
 * 3. 이용권 차감 REFUNDED·RESTORED(이용권 복원), 또는 끝난 신청의 보증금 RETURNED → REFUNDED
 * 4. 끝난 신청: 차감(DEDUCTED)·보증금 입금(PAID·FORFEITED)이 있으면 PAID(환불 없이 끝남), 결제 전에 끝났으면 FAILED
 * 5. 매칭 실패(ALTERNATIVE_OFFERED·NO_MATCH)로 해결 선택 응답 대기 → REFUND_AVAILABLE
 * 6. 승인·출석(이용권 차감·보증금 입금 완료) → PAID, 그 밖(승인 대기·결제 대기) → PENDING
 * 보증금 반환은 신청이 끝났을 때만 환불로 본다(체크인 뒤 반환은 정상 흐름이라 PAID 유지).
 * 우연한 식탁이 아닌 신청: 유료면 입금 확인 여부로 PAID|PENDING, 무료면 null.
 * ponytail: 입금 기한 경과 자동 취소 흐름이 아직 없어 FAILED는 "결제 없이 취소·반려된 신청"이다. 기한 취소가 생기면 그 표시로 좁힌다.
 */
public enum DiningPaymentStatus {
    PENDING, PAID, FAILED, REFUND_AVAILABLE, REFUND_REQUESTED, REFUND_PROCESSING, REFUNDED, REFUND_FAILED;

    public static DiningPaymentStatus of(Application application, TicketDeductionStatus ticket, DepositStatus deposit,
                                         boolean resolutionOffered) {
        if (application.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE) {
            PaymentStatus regular = application.getPaymentStatus();
            return regular == null || regular == PaymentStatus.FREE ? null
                    : regular == PaymentStatus.CONFIRMED ? PAID : PENDING;
        }
        ApplicationStatus status = application.getStatus();
        boolean closed = status == ApplicationStatus.CANCELLED || status == ApplicationStatus.REJECTED;
        DepositStatus refundDeposit = closed ? deposit : null;
        if (ticket == TicketDeductionStatus.REFUND_FAILED) {
            return REFUND_FAILED;
        }
        if (ticket == TicketDeductionStatus.REFUND_REQUESTED) {
            return REFUND_REQUESTED;
        }
        if (ticket == TicketDeductionStatus.REFUND_PROCESSING || refundDeposit == DepositStatus.RETURN_PENDING) {
            return REFUND_PROCESSING;
        }
        if (ticket == TicketDeductionStatus.REFUNDED || ticket == TicketDeductionStatus.RESTORED
                || refundDeposit == DepositStatus.RETURNED) {
            return REFUNDED;
        }
        if (closed) {
            return ticket == TicketDeductionStatus.DEDUCTED || deposit == DepositStatus.PAID
                    || deposit == DepositStatus.FORFEITED ? PAID : FAILED;
        }
        MatchStatus matchStatus = application.getMatchStatus();
        if (resolutionOffered && (matchStatus == MatchStatus.ALTERNATIVE_OFFERED || matchStatus == MatchStatus.NO_MATCH)) {
            return REFUND_AVAILABLE;
        }
        return ApplicationStatus.SEAT_OCCUPYING.contains(status) ? PAID : PENDING;
    }
}
