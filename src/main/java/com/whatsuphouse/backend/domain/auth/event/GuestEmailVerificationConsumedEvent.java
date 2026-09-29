package com.whatsuphouse.backend.domain.auth.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 비회원 신청이 확정(커밋)된 후 이메일 인증을 소비하기 위한 이벤트.
 * 트랜잭션 도중 Redis를 지우면 롤백 시 인증만 소비되는 비원자성 문제가 있어 AFTER_COMMIT으로 처리한다.
 */
@Getter
@RequiredArgsConstructor
public class GuestEmailVerificationConsumedEvent {
    private final String email;
}
