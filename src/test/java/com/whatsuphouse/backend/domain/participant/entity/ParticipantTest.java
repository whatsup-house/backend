package com.whatsuphouse.backend.domain.participant.entity;

import com.whatsuphouse.backend.domain.participant.enums.ParticipantAccountStatus;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipantTest {

    @Test
    @DisplayName("우연한 식탁 승인 상태는 재심사를 건너뛸 수 있다")
    void approveRandomTable_marksApproved() {
        Participant participant = member();

        participant.approveRandomTable();

        assertThat(participant.isApprovedForRandomTable()).isTrue();
        assertThat(participant.isBlockedFromRandomTable()).isFalse();
    }

    @Test
    @DisplayName("우연한 식탁 자격 정지는 신청 제한 상태다")
    void suspendRandomTable_blocksApplication() {
        Participant participant = member();

        participant.suspendRandomTable();

        assertThat(participant.isRandomTableEligibilityRestricted()).isTrue();
        assertThat(participant.isBlockedFromRandomTable()).isTrue();
    }

    @Test
    @DisplayName("참가자 계정 차단은 자격 승인 여부와 무관하게 신청을 제한한다")
    void blockedAccount_overridesApproval() {
        Participant participant = member();
        participant.approveRandomTable();

        participant.changeAccountStatus(ParticipantAccountStatus.BLOCKED);

        assertThat(participant.isApprovedForRandomTable()).isTrue();
        assertThat(participant.isAccountBlocked()).isTrue();
        assertThat(participant.isBlockedFromRandomTable()).isTrue();
    }

    private Participant member() {
        User user = User.builder()
                .email("member@example.com")
                .password("encoded")
                .name("회원")
                .gender(Gender.FEMALE)
                .age(25)
                .nickname("member")
                .phone("01012345678")
                .build();
        return Participant.member(user);
    }
}
