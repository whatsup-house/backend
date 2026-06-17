package com.whatsuphouse.backend.domain.ticket.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MyTicketsResponse {

    private int totalRemaining;
    private List<TicketPassResponse> passes;
}
