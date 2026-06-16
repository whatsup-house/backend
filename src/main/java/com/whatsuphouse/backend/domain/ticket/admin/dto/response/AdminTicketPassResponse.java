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
    private String userNickname;
    private String userName;
    private TicketProduct product;
    private String productLabel;
    private int totalCount;
    private int price;
    private TicketPassStatus status;
    private LocalDateTime createdAt;

    public static AdminTicketPassResponse from(TicketPass pass) {
        User user = pass.getUser();
        return AdminTicketPassResponse.builder()
                .id(pass.getId())
                .userId(user != null ? user.getId() : null)
                .userNickname(user != null ? user.getNickname() : null)
                .userName(user != null ? user.getName() : null)
                .product(pass.getProduct())
                .productLabel(pass.getProduct().getLabel())
                .totalCount(pass.getTotalCount())
                .price(pass.getProduct().getPrice())
                .status(pass.getStatus())
                .createdAt(pass.getCreatedAt())
                .build();
    }
}
