package com.whatsuphouse.backend.domain.participant.client.dto;

import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationListResponse;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.participant.enums.RandomTableEligibility;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GuestOverviewResponse {
    private String name;
    private RandomTableEligibility randomTableEligibility;
    private int totalRemaining;
    private List<TicketPassResponse> passes;
    private List<ApplicationListResponse> applications;

    public static GuestOverviewResponse of(
            Participant participant,
            List<TicketPassResponse> passes,
            List<ApplicationListResponse> applications) {
        int totalRemaining = passes.stream()
                .filter(pass -> pass.getStatus() == TicketPassStatus.ACTIVE)
                .mapToInt(TicketPassResponse::getRemainingCount)
                .sum();
        return GuestOverviewResponse.builder()
                .name(participant.getName())
                .randomTableEligibility(participant.getRandomTableEligibility())
                .totalRemaining(totalRemaining)
                .passes(passes)
                .applications(applications)
                .build();
    }
}
