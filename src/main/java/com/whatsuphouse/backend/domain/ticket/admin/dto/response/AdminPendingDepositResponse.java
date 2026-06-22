package com.whatsuphouse.backend.domain.ticket.admin.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 관리자 입금 대기 큐 한 행.
 * 입금 요청(PENDING 이용권)에 연결된 PAYMENT_PENDING 신청 정보를 붙여,
 * 신청자·게더링 맥락과 함께 빠르게 입금 확인할 수 있게 한다. (게더링 가로지른 시간순 큐)
 */
@Getter
@Builder
public class AdminPendingDepositResponse {

    // 입금 확인 대상. PATCH /api/admin/tickets/{ticketPassId}/confirm 으로 확정한다.
    private UUID ticketPassId;
    private UUID participantId;
    private boolean member;          // 회원 여부 (비회원이면 false)
    private String applicantName;    // 회원=계정 이름, 비회원=참가자 이름
    private TicketProduct product;
    private String productLabel;
    private int amount;              // 입금 안내 금액
    private LocalDateTime requestedAt;
    private LocalDateTime paymentDeadline;

    // 연결된 PAYMENT_PENDING 신청(있을 때만). 입금 확인 시 자동 확정될 대상이다.
    private UUID applicationId;
    private String bookingNumber;
    private UUID gatheringId;
    private String gatheringTitle;

    public static AdminPendingDepositResponse from(TicketPass pass, Application application) {
        Participant participant = pass.getParticipant();
        AdminPendingDepositResponseBuilder builder = AdminPendingDepositResponse.builder()
                .ticketPassId(pass.getId())
                .participantId(participant.getId())
                .member(participant.getUser() != null)
                .applicantName(participant.getName())
                .product(pass.getProduct())
                .productLabel(pass.getProduct().getLabel())
                .amount(pass.getPurchaseAmount())
                .requestedAt(pass.getCreatedAt())
                .paymentDeadline(pass.getPaymentDeadline());
        if (application != null) {
            builder.applicationId(application.getId())
                    .bookingNumber(application.getBookingNumber())
                    .gatheringId(application.getGathering().getId())
                    .gatheringTitle(application.getGathering().getTitle());
        }
        return builder.build();
    }
}
