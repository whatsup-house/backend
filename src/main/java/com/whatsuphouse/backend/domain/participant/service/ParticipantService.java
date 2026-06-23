package com.whatsuphouse.backend.domain.participant.service;

import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.participant.repository.ParticipantRepository;
import com.whatsuphouse.backend.domain.participant.enums.ParticipantType;
import com.whatsuphouse.backend.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ParticipantService {

    private final ParticipantRepository participantRepository;

    /** 회원 참가자를 보장한다. user당 활성 participant 1개. 없으면 생성. */
    public Participant getOrCreateForUser(User user) {
        return participantRepository.findByUser_IdAndDeletedAtIsNull(user.getId())
                .orElseGet(() -> participantRepository.save(Participant.member(user)));
    }

    /**
     * 비회원 참가자를 생성한다. 현재는 신청마다 새 GUEST를 만든다.
     * 사람 단위 식별/이용권 재사용(전화·이메일 매칭)은 후속 일감으로 분리한다. (KAN-276)
     */
    public Participant createGuest(String name, String email, String phone) {
        return participantRepository.save(Participant.guest(name, email, phone));
    }

    public Participant getOrCreateVerifiedGuest(String name, String email, String phone) {
        return participantRepository
                .findFirstByParticipantTypeAndEmailIgnoreCaseAndPhoneAndEmailVerifiedAtIsNotNullAndDeletedAtIsNull(
                        ParticipantType.GUEST, email, phone)
                .orElseGet(() -> {
                    Participant guest = Participant.guest(name, email, phone);
                    guest.verifyEmail();
                    return participantRepository.save(guest);
                });
    }

    /** 전화+이메일로 인증된 비회원 참가자를 찾는다. 비회원 조회("비회원 로그인")에 사용한다. (KAN-292) */
    @Transactional(readOnly = true)
    public Optional<Participant> findVerifiedGuest(String email, String phone) {
        return participantRepository
                .findFirstByParticipantTypeAndEmailIgnoreCaseAndPhoneAndEmailVerifiedAtIsNotNullAndDeletedAtIsNull(
                        ParticipantType.GUEST, email, phone);
    }
}
