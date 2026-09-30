package com.whatsuphouse.backend.domain.chat.controller;

import com.whatsuphouse.backend.domain.chat.dto.request.ChatGroupRoomCreateRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatMemberAddRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatMuteRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatNoticeUpdateRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatReportStatusRequest;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatReportResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomIdResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatSourceMemberResponse;
import com.whatsuphouse.backend.domain.chat.enums.ChatReportStatus;
import com.whatsuphouse.backend.domain.chat.enums.ChatSourceType;
import com.whatsuphouse.backend.domain.chat.service.AdminChatService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "관리자 - 채팅", description = "단체방·멤버·공지·뮤트·신고 관리 API")
@RestController
@RequestMapping("/api/admin/chat")
@RequiredArgsConstructor
public class AdminChatController {

    private final AdminChatService adminChatService;

    @Operation(summary = "단체방 생성", description = "생성한 관리자는 자동 참여. 초대 멤버가 있으면 JOINED 시스템 메시지를 남긴다.")
    @PostMapping("/rooms")
    public ResponseEntity<ApiResult<ChatRoomIdResponse>> createGroupRoom(
            @Valid @RequestBody ChatGroupRoomCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(
                adminChatService.createGroupRoom(principal.getUserId(), principal.isAdmin(), request)));
    }

    @Operation(summary = "전체 방 목록", description = "문의방 미답변 표시·안읽은 수 포함. 미가입 문의방에는 조회한 관리자를 자동 등록한다.")
    @GetMapping("/rooms")
    public ResponseEntity<ApiResult<List<ChatRoomSummaryResponse>>> listRooms(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(adminChatService.listRooms(principal.getUserId(), principal.isAdmin())));
    }

    @Operation(summary = "출처 멤버 불러오기", description = "게더링 참가 확정자 또는 매칭 조원 중 회원 목록(단체방 멤버 프리필)")
    @GetMapping("/rooms/source-members")
    public ResponseEntity<ApiResult<List<ChatSourceMemberResponse>>> listSourceMembers(
            @Parameter(description = "GATHERING | DINING_TABLE", example = "GATHERING") @RequestParam ChatSourceType type,
            @Parameter(description = "게더링 ID 또는 매칭 그룹 ID") @RequestParam UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(adminChatService.listSourceMembers(type, id, principal.isAdmin())));
    }

    @Operation(summary = "멤버 추가", description = "단체방만. 나갔던 멤버는 재초대(전체 기록 공개). JOINED 시스템 메시지.")
    @PostMapping("/rooms/{id}/members")
    public ResponseEntity<ApiResult<Void>> addMembers(
            @Parameter(description = "방 ID") @PathVariable UUID id,
            @Valid @RequestBody ChatMemberAddRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        adminChatService.addMembers(id, principal.isAdmin(), request);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "멤버 내보내기", description = "단체방만. KICKED 시스템 메시지.")
    @DeleteMapping("/rooms/{id}/members/{userId}")
    public ResponseEntity<ApiResult<Void>> kickMember(
            @Parameter(description = "방 ID") @PathVariable UUID id,
            @Parameter(description = "회원 ID") @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        adminChatService.kickMember(id, userId, principal.isAdmin());
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "공지 등록/해제", description = "messageId가 null이면 해제. 등록 시 NOTICE_SET 시스템 메시지.")
    @PutMapping("/rooms/{id}/notice")
    public ResponseEntity<ApiResult<Void>> changeNotice(
            @Parameter(description = "방 ID") @PathVariable UUID id,
            @Valid @RequestBody ChatNoticeUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        adminChatService.changeNotice(id, principal.getUserId(), principal.isAdmin(), request);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "방 삭제", description = "단체방 소프트 삭제")
    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<ApiResult<Void>> deleteRoom(
            @Parameter(description = "방 ID") @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        adminChatService.deleteRoom(id, principal.isAdmin());
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "채팅 금지(뮤트)", description = "계정 정지와 별개. 이미 뮤트면 사유를 갱신한다.")
    @PutMapping("/mutes/{userId}")
    public ResponseEntity<ApiResult<Void>> muteUser(
            @Parameter(description = "회원 ID") @PathVariable UUID userId,
            @Valid @RequestBody ChatMuteRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        adminChatService.muteUser(userId, principal.getUserId(), principal.isAdmin(), request);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "채팅 금지 해제")
    @DeleteMapping("/mutes/{userId}")
    public ResponseEntity<ApiResult<Void>> unmuteUser(
            @Parameter(description = "회원 ID") @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        adminChatService.unmuteUser(userId, principal.isAdmin());
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "신고 목록", description = "status 미지정 시 전체, 최신순")
    @GetMapping("/reports")
    public ResponseEntity<ApiResult<List<ChatReportResponse>>> listReports(
            @Parameter(description = "OPEN | RESOLVED") @RequestParam(required = false) ChatReportStatus status,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(adminChatService.listReports(status, principal.isAdmin())));
    }

    @Operation(summary = "신고 처리 상태 변경")
    @PatchMapping("/reports/{id}")
    public ResponseEntity<ApiResult<Void>> changeReportStatus(
            @Parameter(description = "신고 ID") @PathVariable UUID id,
            @Valid @RequestBody ChatReportStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        adminChatService.changeReportStatus(id, principal.isAdmin(), request);
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
