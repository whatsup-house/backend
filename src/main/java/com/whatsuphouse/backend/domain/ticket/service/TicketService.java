package com.whatsuphouse.backend.domain.ticket.service;

import com.whatsuphouse.backend.domain.ticket.dto.response.MyTicketsResponse;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.participant.service.ParticipantService;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class TicketService {

    private final TicketPassRepository ticketPassRepository;
    private final UserRepository userRepository;
    private final ParticipantService participantService;

    /** 이용권 구매(선결제) 요청. 입금 확인 전이므로 PENDING으로 생성된다. */
    public TicketPassResponse purchase(UUID userId, TicketProduct product) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Participant participant = participantService.getOrCreateForUser(user);
        TicketPass pass = TicketPass.builder()
                .participant(participant)
                .product(product)
                .build();
        ticketPassRepository.save(pass);
        return TicketPassResponse.from(pass);
    }

    @Transactional(readOnly = true)
    public MyTicketsResponse getMyTickets(UUID userId) {
        List<TicketPass> passes = ticketPassRepository.findByParticipant_User_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        int totalRemaining = passes.stream()
                .filter(p -> p.getStatus() == TicketPassStatus.ACTIVE)
                .mapToInt(TicketPass::getRemainingCount)
                .sum();
        List<TicketPassResponse> items = passes.stream()
                .map(TicketPassResponse::from)
                .toList();
        return MyTicketsResponse.builder()
                .totalRemaining(totalRemaining)
                .passes(items)
                .build();
    }

    /** 우연한 식탁 신청 시 1회 차감한다. 사용 가능한 이용권이 없으면 NO_AVAILABLE_TICKET. */
    public void useOneTicket(User user) {
        Participant participant = participantService.getOrCreateForUser(user);
        if (!tryUseOneTicket(participant)) {
            throw new CustomException(ErrorCode.NO_AVAILABLE_TICKET);
        }
    }

    /** 참가자 기준으로 잔여 이용권을 1회 차감한다. 이용권이 없으면 상태 변경 없이 false를 반환한다. */
    public boolean tryUseOneTicket(Participant participant) {
        List<TicketPass> usable = ticketPassRepository.findUsableByParticipantForUpdate(
                participant.getId(), TicketPassStatus.ACTIVE, PageRequest.of(0, 1));
        if (usable.isEmpty()) {
            return false;
        }
        usable.get(0).deductOne();
        return true;
    }

    /** 우연한 식탁 신청 취소 시 차감했던 이용권을 1회 환불한다. 환불 대상이 없으면 무시. */
    public void refundOneTicket(User user) {
        ticketPassRepository.findByParticipant_User_IdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId()).stream()
                .filter(p -> p.getStatus() == TicketPassStatus.USED_UP
                        || (p.getStatus() == TicketPassStatus.ACTIVE && p.getRemainingCount() < p.getTotalCount()))
                .findFirst()
                .ifPresent(TicketPass::refundOne);
    }
}
