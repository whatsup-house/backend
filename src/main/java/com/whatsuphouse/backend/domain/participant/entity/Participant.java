package com.whatsuphouse.backend.domain.participant.entity;

import com.whatsuphouse.backend.domain.participant.enums.ParticipantType;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 모임 참가자 (회원/비회원 공통). 신청·이용권의 소유 주체다.
 * 회원은 user_id로 users와 1:1 연결, 비회원은 user_id NULL.
 */
@Entity
@Table(name = "participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 회원 참가자만 users.id를 참조한다. 비회원은 NULL.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 20)
    private ParticipantType participantType;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 11)
    private String phone;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    // 사람 단위 자격/상태. 현재는 기본값으로만 저장하고, 심사·차단 로직은 후속 일감에서 사용한다. (KAN-277)
    @Column(name = "account_status", nullable = false, length = 20)
    private String accountStatus;

    @Column(name = "random_table_eligibility", nullable = false, length = 20)
    private String randomTableEligibility;

    private Participant(User user, ParticipantType participantType, String name, String email, String phone) {
        this.user = user;
        this.participantType = participantType;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.accountStatus = "ACTIVE";
        this.randomTableEligibility = "UNREVIEWED";
    }

    /** 회원 참가자 생성. 이름/이메일/전화는 계정 정보를 따른다. */
    public static Participant member(User user) {
        return new Participant(user, ParticipantType.MEMBER, user.getName(), user.getEmail(), user.getPhone());
    }

    /** 비회원 참가자 생성. 이메일이 없으면 빈 문자열로 저장한다(NOT NULL). */
    public static Participant guest(String name, String email, String phone) {
        return new Participant(null, ParticipantType.GUEST, name, email != null ? email : "", phone);
    }

    public boolean isMember() {
        return this.participantType == ParticipantType.MEMBER;
    }
}
