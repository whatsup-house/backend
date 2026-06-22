package com.whatsuphouse.backend.domain.ticket.dto.response;

import lombok.Builder;
import lombok.Getter;
import com.whatsuphouse.backend.domain.participant.enums.ParticipantAccountStatus;
import com.whatsuphouse.backend.domain.participant.enums.RandomTableEligibility;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class MyTicketsResponse {

    private ParticipantAccountStatus accountStatus;
    private RandomTableEligibility randomTableEligibility;
    private boolean purchasable;
    private int totalRemaining;
    private List<TicketPassResponse> passes;
    private UUID gatheringId;
}
