package com.whatsuphouse.backend.domain.notification.event;

import com.whatsuphouse.backend.domain.application.entity.Application;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ApplicationAttendedEvent {
    private final Application application;
    private final int mileageEarned;
    private final int mileageBalance;
}
