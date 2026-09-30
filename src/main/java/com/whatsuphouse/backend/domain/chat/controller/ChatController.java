package com.whatsuphouse.backend.domain.chat.controller;

import com.whatsuphouse.backend.domain.chat.dto.request.ChatMessageSendRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatMessageUpdateRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatReportCreateRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.PushSubscriptionCreateRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.PushSubscriptionDeleteRequest;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatImageUploadResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatMessageResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomDetailResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomIdResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatSocketTokenResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.PushPublicKeyResponse;
import com.whatsuphouse.backend.domain.chat.service.ChatPushService;
import com.whatsuphouse.backend.domain.chat.service.ChatService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "채팅", description = "채팅방·메시지 API (실시간 수신은 STOMP 별도)")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatPushService chatPushService;

    @Operation(summary = "내 방 목록", description = "마지막 메시지·안읽은 수 포함, 숨긴 방 제외. 최근 활동순.")
    @GetMapping("/rooms")
    public ResponseEntity<ApiResult<List<ChatRoomSummaryResponse>>> listRooms(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(chatService.listRooms(principal.getUserId(), principal.isAdmin())));
    }

    @Operation(summary = "문의방 열기", description = "와썹하우스 문의방을 연다. 없으면 생성하고, 숨겨져 있으면 해제한다.")
    @PostMapping("/rooms/inquiry")
    public ResponseEntity<ApiResult<ChatRoomIdResponse>> openInquiryRoom(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(chatService.openInquiryRoom(principal.getUserId(), principal.isAdmin())));
    }

    @Operation(summary = "방 상세", description = "멤버, 공지, 내 권한")
    @GetMapping("/rooms/{id}")
    public ResponseEntity<ApiResult<ChatRoomDetailResponse>> getRoom(
            @Parameter(description = "방 ID") @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(chatService.getRoom(id, principal.getUserId(), principal.isAdmin())));
    }

    @Operation(summary = "메시지 목록", description = "before/after는 메시지 ID 커서(after 우선). 결과는 오래된 순. 재초대 멤버도 전체 기록을 본다.")
    @GetMapping("/rooms/{id}/messages")
    public ResponseEntity<ApiResult<List<ChatMessageResponse>>> listMessages(
            @Parameter(description = "방 ID") @PathVariable UUID id,
            @Parameter(description = "이 메시지보다 이전") @RequestParam(required = false) UUID before,
            @Parameter(description = "이 메시지보다 이후(재접속 보충)") @RequestParam(required = false) UUID after,
            @Parameter(description = "개수(1~100)", example = "50") @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(
                chatService.listMessages(id, principal.getUserId(), principal.isAdmin(), before, after, size)));
    }

    @Operation(summary = "메시지 전송", description = "TEXT는 2000자 이하. IMAGE는 업로드 API가 돌려준 path를 content로 보낸다.")
    @PostMapping("/rooms/{id}/messages")
    public ResponseEntity<ApiResult<ChatMessageResponse>> sendMessage(
            @Parameter(description = "방 ID") @PathVariable UUID id,
            @Valid @RequestBody ChatMessageSendRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(
                chatService.sendMessage(id, principal.getUserId(), principal.isAdmin(), request)));
    }

    @Operation(summary = "메시지 수정", description = "본인 TEXT 메시지만")
    @PatchMapping("/messages/{id}")
    public ResponseEntity<ApiResult<ChatMessageResponse>> updateMessage(
            @Parameter(description = "메시지 ID") @PathVariable UUID id,
            @Valid @RequestBody ChatMessageUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(
                chatService.updateMessage(id, principal.getUserId(), principal.isAdmin(), request)));
    }

    @Operation(summary = "메시지 삭제", description = "소프트 삭제. 본인 또는 관리자")
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<ApiResult<Void>> deleteMessage(
            @Parameter(description = "메시지 ID") @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        chatService.deleteMessage(id, principal.getUserId(), principal.isAdmin());
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "리액션 토글", description = "허용 이모지: 👍 ❤️ 😂 😮 😢 🙏. 결과로 해당 메시지의 리액션 집계를 돌려준다.")
    @PutMapping("/messages/{id}/reactions/{emoji}")
    public ResponseEntity<ApiResult<List<ChatMessageResponse.Reaction>>> toggleReaction(
            @Parameter(description = "메시지 ID") @PathVariable UUID id,
            @Parameter(description = "이모지(URL 인코딩)", example = "👍") @PathVariable String emoji,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(chatService.toggleReaction(id, emoji, principal.getUserId())));
    }

    @Operation(summary = "메시지 신고")
    @PostMapping("/messages/{id}/report")
    public ResponseEntity<ApiResult<Void>> reportMessage(
            @Parameter(description = "메시지 ID") @PathVariable UUID id,
            @Valid @RequestBody ChatReportCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        chatService.reportMessage(id, principal.getUserId(), principal.isAdmin(), request);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "조용히 나가기", description = "단체방만. 시스템 메시지를 남기지 않는다.")
    @PostMapping("/rooms/{id}/leave")
    public ResponseEntity<ApiResult<Void>> leaveRoom(
            @Parameter(description = "방 ID") @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        chatService.leaveRoom(id, principal.getUserId());
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "문의방 숨기기", description = "문의방만. 다시 열거나 새 메시지가 오면 목록에 보인다.")
    @PostMapping("/rooms/{id}/hide")
    public ResponseEntity<ApiResult<Void>> hideRoom(
            @Parameter(description = "방 ID") @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        chatService.hideRoom(id, principal.getUserId(), principal.isAdmin());
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "채팅 이미지 업로드", description = "jpg/jpeg/png/webp, 5MB 이하. 비공개 버킷에 저장하고 path와 1시간 서명 URL을 돌려준다.")
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResult<ChatImageUploadResponse>> uploadImage(
            @Parameter(description = "이미지 파일") @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(chatService.uploadImage(file, principal.getUserId())));
    }

    @Operation(summary = "채팅 소켓 토큰 발급",
            description = "쿠키 인증 필수. STOMP CONNECT 의 Authorization: Bearer 헤더에 넣을 단기 토큰(기본 2분)을 발급한다. "
                    + "CONNECT 시점에만 검증하므로 연결 중 만료돼도 끊기지 않는다. REST 인증에는 쓸 수 없다. 정지·탈퇴 계정은 403.")
    @GetMapping("/socket-token")
    public ResponseEntity<ApiResult<ChatSocketTokenResponse>> getSocketToken(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(chatService.createSocketToken(principal)));
    }

    @Operation(summary = "웹 푸시 공개키", description = "VAPID 공개키. PushManager.subscribe의 applicationServerKey로 쓴다. VAPID 미설정이면 503.")
    @GetMapping("/push-subscriptions/public-key")
    public ResponseEntity<ApiResult<PushPublicKeyResponse>> getPushPublicKey() {
        return ResponseEntity.ok(ApiResult.success(chatPushService.getPublicKey()));
    }

    @Operation(summary = "웹 푸시 구독 등록", description = "PushSubscription.toJSON() 그대로. 같은 endpoint 재등록은 갱신. VAPID 미설정이면 503.")
    @PostMapping("/push-subscriptions")
    public ResponseEntity<ApiResult<Void>> registerPushSubscription(
            @Valid @RequestBody PushSubscriptionCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        chatPushService.registerSubscription(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(null));
    }

    @Operation(summary = "웹 푸시 구독 해제", description = "본인 구독만. 없어도 204. VAPID 미설정이면 503.")
    @DeleteMapping("/push-subscriptions")
    public ResponseEntity<Void> deletePushSubscription(
            @Valid @RequestBody PushSubscriptionDeleteRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        chatPushService.deleteSubscription(principal.getUserId(), request.getEndpoint());
        return ResponseEntity.noContent().build();
    }
}
