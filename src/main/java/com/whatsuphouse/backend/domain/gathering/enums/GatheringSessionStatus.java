package com.whatsuphouse.backend.domain.gathering.enums;

public enum GatheringSessionStatus {
    OPEN, CLOSED, DONE, CANCELLED;

    // 모임·회차 API 개편(KAN-338) 전까지 요청·응답은 기존 GatheringStatus(COMPLETED)를 유지한다.
    public static GatheringSessionStatus from(GatheringStatus status) {
        return status == GatheringStatus.COMPLETED ? DONE : valueOf(status.name());
    }

    public GatheringStatus toGatheringStatus() {
        return this == DONE ? GatheringStatus.COMPLETED : GatheringStatus.valueOf(name());
    }
}
