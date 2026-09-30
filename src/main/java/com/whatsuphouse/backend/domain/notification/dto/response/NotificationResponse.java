package com.whatsuphouse.backend.domain.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsuphouse.backend.domain.notification.entity.Notification;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class NotificationResponse {

    private UUID id;
    private NotificationType type;
    private String title;
    private String content;
    private NotificationLink link;
    @Schema(description = "link 대상 ID(DINING_TABLE이면 테이블 ID). 대상이 없는 링크는 null")
    private UUID linkId;
    @JsonProperty("isRead")
    private boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .link(notification.getLink())
                .linkId(notification.getLinkId())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
