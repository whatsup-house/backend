package com.whatsuphouse.backend.domain.user.entity;

import com.whatsuphouse.backend.domain.user.enums.UserAccountStatus;
import com.whatsuphouse.backend.global.common.BaseEntity;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(length = 11)
    private String phone;

    @Column(name = "instagram_id", length = 100)
    private String instagramId;

    @Enumerated(EnumType.STRING)
    @Column(length = 4)
    private Mbti mbti;

    @Column(length = 30)
    private String job;

    @Column(columnDefinition = "TEXT")
    private String intro;

    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin = false;

    @Column(name = "mileage_balance", nullable = false)
    private Integer mileageBalance = 0;

    // 계정 상태(정상/정지). 기존 데이터 호환을 위해 nullable이며, null은 ACTIVE로 간주한다. (KAN-188)
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", length = 20)
    private UserAccountStatus accountStatus = UserAccountStatus.ACTIVE;

    @Builder
    public User(String email, String password, String name, Gender gender, Integer age, String nickname, String phone) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.gender = gender;
        this.age = age;
        this.nickname = nickname;
        this.phone = phone;
    }

    public void updateProfile(String nickname, String phone, String name, Gender gender, Integer age,
                              String instagramId, Mbti mbti, String job, String intro) {
        this.nickname = nickname;
        this.phone = phone;
        this.name = name;
        this.gender = gender;
        this.age = age;
        this.instagramId = instagramId;
        this.mbti = mbti;
        this.job = job;
        this.intro = intro;
    }

    public void changeAccountStatus(UserAccountStatus status) {
        this.accountStatus = status;
    }

    /** 기존 데이터(null)는 ACTIVE로 간주한다. */
    public UserAccountStatus getEffectiveAccountStatus() {
        return accountStatus != null ? accountStatus : UserAccountStatus.ACTIVE;
    }

    public Integer addMileage(int amount) {
        this.mileageBalance += amount;
        return this.mileageBalance;
    }

    public Integer deductMileage(int amount) {
        this.mileageBalance -= amount;
        return this.mileageBalance;
    }

}
