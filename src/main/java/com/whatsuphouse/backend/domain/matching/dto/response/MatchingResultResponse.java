package com.whatsuphouse.backend.domain.matching.dto.response;

import com.whatsuphouse.backend.domain.matching.enums.MatchingGroupStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class MatchingResultResponse {

    private UUID gatheringId;
    private List<GroupView> groups;
    private List<MemberView> unmatched;

    @Getter
    @Builder
    public static class GroupView {
        private UUID groupId;
        private LocalDate eventDate;
        private MatchingGroupStatus status;
        private BigDecimal groupScore;
        private int groupSize;
        private String restaurantName;
        private String restaurantAddress;
        private List<MemberView> members;
    }

    @Getter
    @Builder
    public static class MemberView {
        private UUID memberId;       // 미배정자는 null
        private UUID applicationId;
        private String name;
        private String phone;
        private Integer seatOrder;
        private boolean manualAssign;
    }
}
