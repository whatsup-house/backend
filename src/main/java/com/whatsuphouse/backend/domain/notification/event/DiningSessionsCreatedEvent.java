package com.whatsuphouse.backend.domain.notification.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/** 관리자가 우연한 식탁 회차를 만듦(반복 생성이면 한 번의 요청에 1건). 다음 모집 알림을 보낸다. (KAN-349) */
@Getter
@RequiredArgsConstructor
public class DiningSessionsCreatedEvent {
    private final UUID gatheringId;
    private final String gatheringTitle;
    // 회차 장소. 우연한 식탁 회차는 장소가 선택이라 null일 수 있다. (KAN-430)
    private final UUID locationId;
    private final String locationName;
}
