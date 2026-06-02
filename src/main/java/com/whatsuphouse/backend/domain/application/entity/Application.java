package com.whatsuphouse.backend.domain.application.entity;

import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.BaseEntity;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> formSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Builder
    public Application(String bookingNumber, Gathering gathering, User user, String name, String phone,
                       Map<String, Object> formSnapshot) {
        this.bookingNumber = bookingNumber;
        this.gathering = gathering;
        this.user = user;
        this.name = name;
        this.phone = phone;
        this.formSnapshot = formSnapshot;
        this.status = ApplicationStatus.PENDING;
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
