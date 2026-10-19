package com.whatsuphouse.backend.domain.bot.controller;

import com.whatsuphouse.backend.domain.bot.dto.request.BotReplyRequest;
import com.whatsuphouse.backend.domain.bot.service.BotReplyService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "그록봇", description = "외부 AI 에이전트(그록봇) 답변 수신 API. Authorization: Bearer {봇 토큰} 전용(일반 JWT 불가)")
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class BotReplyController {

    private final BotReplyService botReplyService;

    @Operation(summary = "문의 답변 등록", description = "reply: 답변 게시 / handoff: 답변 게시 + 담당자 이관. "
            + "404 dispatch 없음, 403 방·메시지 불일치, 409 이미 처리·더 새 문의·대기 상태 아님, 422 Idempotency-Key 불일치·본문 길이")
    @PostMapping("/replies")
    public ResponseEntity<ApiResult<Void>> reply(
            @Parameter(description = "eventId와 같은 값") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BotReplyRequest request
    ) {
        botReplyService.reply(idempotencyKey, request);
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
