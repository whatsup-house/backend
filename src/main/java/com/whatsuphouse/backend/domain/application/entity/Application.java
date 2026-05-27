package com.whatsuphouse.backend.domain.application.entity;

import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.JobCategory;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.BaseEntity;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_number", nullable = false, unique = true, length = 20)
    private String bookingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 11)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    private Integer age;

    @Column(name = "instagram_id", length = 100)
    private String instagramId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_category", nullable = false, length = 30)
    private JobCategory jobCategory;

    @Column(name = "job_detail", length = 100)
    private String jobDetail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> formSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(length = 4)
    private Mbti mbti;

    @Column(columnDefinition = "TEXT")
    private String intro;

    @Column(name = "referrer_name", length = 50)
    private String referrerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Builder
    public Application(String bookingNumber, Gathering gathering, User user, String name, String phone,
                       Gender gender, Integer age, String instagramId, String job, Mbti mbti,
                       String intro, String referrerName, JobCategory jobCategory, String jobDetail,
                       Map<String, Object> formSnapshot) {
        this.bookingNumber = bookingNumber;
        this.gathering = gathering;
        this.user = user;
        this.name = name;
        this.phone = phone;
        this.gender = gender;
        this.age = age;
        this.instagramId = instagramId;
        this.jobCategory = jobCategory != null ? jobCategory : JobCategory.ETC;
        this.jobDetail = jobDetail != null ? jobDetail : job;
        this.formSnapshot = formSnapshot;
        this.mbti = mbti;
        this.intro = intro;
        this.referrerName = referrerName;
        this.status = ApplicationStatus.PENDING;
    }

    public String getJob() {
        return jobDetail;
    }

    public void cancel() {
        this.status = ApplicationStatus.CANCELLED;
        delete();
    }

    public void confirm() {
        this.status = ApplicationStatus.CONFIRMED;
    }

    public void attend() {
        this.status = ApplicationStatus.ATTENDED;
    }
}
