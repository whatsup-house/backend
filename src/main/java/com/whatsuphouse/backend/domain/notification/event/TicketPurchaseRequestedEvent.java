package com.whatsuphouse.backend.domain.notification.event;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TicketPurchaseRequestedEvent {
    private final Application application;
    private final TicketPass ticketPass;
}
