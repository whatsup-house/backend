package com.whatsuphouse.backend.domain.gathering.enums;

public enum GatheringSessionStatus {
    OPEN, CLOSED, DONE, CANCELLED;

    // 종류·회차 API(KAN-338)는 이 값을 그대로 쓴다. 관리자 목록·상태 변경, 큐레이션, 캐러셀 등 기존 API는
    // 아직 GatheringStatus(COMPLETED)로 주고받으므로 변환을 둔다.
    public static GatheringSessionStatus from(GatheringStatus status) {
        return status == GatheringStatus.COMPLETED ? DONE : valueOf(status.name());
    }

    public GatheringStatus toGatheringStatus() {
        return this == DONE ? GatheringStatus.COMPLETED : GatheringStatus.valueOf(name());
    }
}
