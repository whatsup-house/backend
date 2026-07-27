package com.whatsuphouse.backend.domain.notification.event;

import com.whatsuphouse.backend.domain.user.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class WelcomeEvent {
    private final User user;
}
