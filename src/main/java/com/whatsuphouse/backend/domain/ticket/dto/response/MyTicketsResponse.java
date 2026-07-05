package com.whatsuphouse.backend.domain.ticket.dto.response;

import lombok.Builder;
import lombok.Getter;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.user.enums.RandomTableEligibility;
import com.whatsuphouse.backend.domain.user.enums.UserAccountStatus;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class MyTicketsResponse {

    private UserAccountStatus accountStatus;
    private RandomTableEligibility randomTableEligibility;
    private boolean purchasable;
    private int totalRemaining;
    private List<TicketPassResponse> passes;
    private UUID applicationId;
    private String bookingNumber;
    private UUID gatheringId;
    private ApplicationStatus applicationStatus;
}
