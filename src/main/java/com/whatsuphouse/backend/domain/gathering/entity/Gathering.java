package com.whatsuphouse.backend.domain.gathering.entity;

import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "gatherings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Gathering extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "how_to_run", columnDefinition = "jsonb")
    private List<String> howToRun = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags = List.of();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    private Integer price;

    @Column(name = "max_attendees", nullable = false)
    private int maxAttendees;

    @Enumerated(EnumType.STRING)
    @Column(name = "gathering_type", length = 20)
    private GatheringType gatheringType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GatheringStatus status = GatheringStatus.OPEN;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "is_curated", nullable = false)
    private boolean isCurated = false;

    @Column(name = "curated_rank", nullable = false)
    private int curatedRank = 0;

    @Builder
    public Gathering(String title, String description, Location location, LocalDate eventDate,
                     LocalTime startTime, LocalTime endTime, Integer price, int maxAttendees, String thumbnailUrl,
                     GatheringType gatheringType, List<String> howToRun, List<String> tags) {
        this.title = title;
        this.description = description;
        this.howToRun = howToRun != null ? List.copyOf(howToRun) : List.of();
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.location = location;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.price = price;
        this.maxAttendees = maxAttendees;
        this.thumbnailUrl = thumbnailUrl;
        this.gatheringType = gatheringType != null ? gatheringType : GatheringType.REGULAR;
        this.status = GatheringStatus.OPEN;
    }

    public void changeStatus(GatheringStatus status) {
        this.status = status;
    }

    /**
     * 사용자 응답용 유효 상태. eventDate가 지난 모집중(OPEN) 게더링은 진행 완료(COMPLETED)로 간주한다. (KAN-163)
     * CANCELLED, CLOSED, COMPLETED 등 명시적 상태는 그대로 유지한다.
     */
    public GatheringStatus getEffectiveStatus() {
        if (status == GatheringStatus.OPEN && eventDate.isBefore(LocalDate.now())) {
            return GatheringStatus.COMPLETED;
        }
        return status;
    }

    public void updateCuration(boolean isCurated) {
        this.isCurated = isCurated;
    }

    public void updateCuratedRank(int rank) {
        this.curatedRank = rank;
    }

    public void update(String title, String description, Location location, LocalDate eventDate,
                       LocalTime startTime, LocalTime endTime, Integer price, int maxAttendees, String thumbnailUrl,
                       List<String> howToRun, List<String> tags) {
        this.title = title;
        this.description = description;
        this.howToRun = howToRun != null ? List.copyOf(howToRun) : List.of();
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.location = location;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.price = price;
        this.maxAttendees = maxAttendees;
        this.thumbnailUrl = thumbnailUrl;
    }
}
