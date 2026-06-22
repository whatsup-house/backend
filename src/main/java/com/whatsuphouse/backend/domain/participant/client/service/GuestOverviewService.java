package com.whatsuphouse.backend.domain.participant.client.service;

import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationListResponse;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.auth.service.AuthService;
import com.whatsuphouse.backend.domain.participant.client.dto.GuestOverviewResponse;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.participant.enums.ParticipantType;
import com.whatsuphouse.backend.domain.participant.repository.ParticipantRepository;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestOverviewService {
    private final AuthService authService;
    private final ParticipantRepository participantRepository;
    private final TicketPassRepository ticketPassRepository;
    private final ApplicationRepository applicationRepository;

    public GuestOverviewResponse getOverview(String phone, String email) {
        if (!authService.isGuestEmailVerified(email)) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        Participant participant = participantRepository
                .findFirstByParticipantTypeAndEmailIgnoreCaseAndPhoneAndEmailVerifiedAtIsNotNullAndDeletedAtIsNull(
                        ParticipantType.GUEST, email.trim(), phone.trim())
                .orElseThrow(() -> new CustomException(ErrorCode.GUEST_PARTICIPANT_NOT_FOUND));

        var passes = ticketPassRepository
                .findByParticipant_IdAndDeletedAtIsNullOrderByCreatedAtDesc(participant.getId())
                .stream().map(TicketPassResponse::from).toList();
        var applications = applicationRepository
                .findByParticipant_IdAndDeletedAtIsNullOrderByCreatedAtDesc(participant.getId())
                .stream().map(ApplicationListResponse::from).toList();
        return GuestOverviewResponse.of(participant, passes, applications);
    }
}
