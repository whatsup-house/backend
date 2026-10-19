package com.whatsuphouse.backend.domain.bot.admin.controller;

import com.whatsuphouse.backend.domain.bot.dto.request.BotSettingUpdateRequest;
import com.whatsuphouse.backend.domain.bot.dto.response.BotSettingResponse;
import com.whatsuphouse.backend.domain.bot.service.BotDispatchService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 - 그록봇", description = "문의방 AI 자동응답 전역 토글")
@RestController
@RequestMapping("/api/admin/bot")
@RequiredArgsConstructor
public class AdminBotController {

    private final BotDispatchService botDispatchService;

    @Operation(summary = "그록봇 설정 조회", description = "enabled=관리자 토글, active=서버 설정까지 포함한 실제 동작 여부")
    @GetMapping("/settings")
    public ResponseEntity<ApiResult<BotSettingResponse>> getSettings() {
        return ResponseEntity.ok(ApiResult.success(botDispatchService.getSettings()));
    }

    @Operation(summary = "그록봇 전역 토글 변경")
    @PutMapping("/settings")
    public ResponseEntity<ApiResult<BotSettingResponse>> updateSettings(
            @Valid @RequestBody BotSettingUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(
                botDispatchService.updateSettings(request.getEnabled(), principal.getUserId())));
    }
}
