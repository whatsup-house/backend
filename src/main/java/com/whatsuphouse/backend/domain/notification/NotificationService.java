package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.user.entity.User;

import java.util.List;

public interface NotificationService {

    void sendWelcome(User user);

    void sendPasswordReset(User user, String resetUrl);

    void sendApplicationPending(Application application);

    void sendApplicationConfirmed(Application application);

    void sendApplicationCancelled(Application application);

    void sendApplicationAttended(Application application, int mileageEarned, int mileageBalance);

    void sendGatheringCancelled(Gathering gathering, List<Application> applications);
}
