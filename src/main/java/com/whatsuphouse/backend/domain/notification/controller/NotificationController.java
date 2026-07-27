package com.whatsuphouse.backend.domain.notification.controller;

import com.whatsuphouse.backend.domain.notification.dto.response.NotificationResponse;
import com.whatsuphouse.backend.domain.notification.dto.response.UnreadCountResponse;
import com.whatsuphouse.backend.domain.notification.service.UserNotificationService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "알림", description = "인앱 알림 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final UserNotificationService userNotificationService;

    @Operation(summary = "내 알림 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResult<List<NotificationResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(
                userNotificationService.listMyNotifications(principal.getUserId())));
    }

    @Operation(summary = "미확인 알림 개수 조회")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResult<UnreadCountResponse>> unreadCount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(
                new UnreadCountResponse(userNotificationService.getUnreadCount(principal.getUserId()))));
    }

    @Operation(summary = "알림 읽음 처리")
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResult<Void>> markRead(
            @Parameter(description = "알림 ID") @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        userNotificationService.markAsRead(id, principal.getUserId());
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "알림 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResult<Void>> delete(
            @Parameter(description = "알림 ID") @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        userNotificationService.delete(id, principal.getUserId());
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
