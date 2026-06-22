package com.whatsuphouse.backend.domain.ticket.admin.dto.response;

import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import com.whatsuphouse.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class AdminTicketPassResponse {

    private UUID id;
    private UUID userId;
    private UUID participantId;
    private String userNickname;
    private String userName;
    private TicketProduct product;
    private String productLabel;
    private int totalCount;
    private int remainingCount;
    private int price;
    private TicketPassStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime paymentDeadline;
    private LocalDateTime paymentConfirmedAt;

    public static AdminTicketPassResponse from(TicketPass pass) {
        User user = pass.getUser();
        return AdminTicketPassResponse.builder()
                .id(pass.getId())
                .userId(user != null ? user.getId() : null)
                .participantId(pass.getParticipant().getId())
                .userNickname(user != null ? user.getNickname() : null)
                .userName(user != null ? user.getName() : null)
                .product(pass.getProduct())
                .productLabel(pass.getProduct().getLabel())
                .totalCount(pass.getTotalCount())
                .remainingCount(pass.getRemainingCount())
                .price(pass.getPurchaseAmount())
                .status(pass.getStatus())
                .createdAt(pass.getCreatedAt())
                .paymentDeadline(pass.getPaymentDeadline())
                .paymentConfirmedAt(pass.getPaymentConfirmedAt())
                .build();
    }
}
