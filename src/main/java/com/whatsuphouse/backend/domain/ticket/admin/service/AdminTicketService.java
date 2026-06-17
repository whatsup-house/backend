package com.whatsuphouse.backend.domain.ticket.admin.service;

import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminTicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminTicketService {

    private final TicketPassRepository ticketPassRepository;

    @Transactional(readOnly = true)
    public List<AdminTicketPassResponse> listPending() {
        return ticketPassRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(TicketPassStatus.PENDING)
                .stream()
                .map(AdminTicketPassResponse::from)
                .toList();
    }

    /** 입금 확인 → 이용권 활성화(잔여 충전). PENDING이 아니면 TICKET_ALREADY_PROCESSED. */
    public AdminTicketPassResponse confirm(UUID passId) {
        TicketPass pass = ticketPassRepository.findByIdAndDeletedAtIsNull(passId)
                .orElseThrow(() -> new CustomException(ErrorCode.TICKET_PASS_NOT_FOUND));
        pass.activate();
        return AdminTicketPassResponse.from(pass);
    }
}
