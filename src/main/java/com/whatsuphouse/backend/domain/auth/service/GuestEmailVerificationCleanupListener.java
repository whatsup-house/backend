package com.whatsuphouse.backend.domain.auth.service;

import com.whatsuphouse.backend.domain.auth.event.GuestEmailVerificationConsumedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 신청 트랜잭션 커밋 후에만 비회원 이메일 인증(Redis)을 소비한다.
 * 신청이 롤백되면 인증은 그대로 남아 재신청이 가능하다.
 */
@Component
@RequiredArgsConstructor
public class GuestEmailVerificationCleanupListener {

    private final AuthService authService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GuestEmailVerificationConsumedEvent event) {
        authService.consumeGuestEmailVerification(event.getEmail());
    }
}
