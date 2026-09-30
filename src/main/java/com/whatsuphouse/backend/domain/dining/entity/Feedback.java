package com.whatsuphouse.backend.domain.dining.entity;

import com.whatsuphouse.backend.domain.dining.enums.RejoinIntent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/** 테이블 피드백. 테이블 멤버(dining_table_members.id)당 1회. (설계 2.6, KAN-350) */
@Entity
@Table(name = "feedbacks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "table_member_id", nullable = false, unique = true)
    private UUID tableMemberId;

    @Column(name = "table_score", nullable = false)
    private int tableScore;

    @Column(name = "talk_score", nullable = false)
    private int talkScore;

    @Column(name = "venue_score", nullable = false)
    private int venueScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejoin_intent", nullable = false, length = 10)
    private RejoinIntent rejoinIntent;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Feedback(UUID tableMemberId, int tableScore, int talkScore, int venueScore, RejoinIntent rejoinIntent,
                    String comment) {
        this.tableMemberId = tableMemberId;
        this.tableScore = tableScore;
        this.talkScore = talkScore;
        this.venueScore = venueScore;
        this.rejoinIntent = rejoinIntent;
        this.comment = comment;
    }
}
