package com.whatsuphouse.backend.domain.notification.event;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class GatheringCancelledEvent {
    private final Gathering gathering;
    private final List<Application> applications;
}
