package com.whatsuphouse.backend.domain.bot.controller;

import com.whatsuphouse.backend.domain.bot.dto.request.BotReplyRequest;
import com.whatsuphouse.backend.domain.bot.dto.response.BotPendingResponse;
import com.whatsuphouse.backend.domain.bot.service.BotDispatchService;
import com.whatsuphouse.backend.domain.bot.service.BotReplyService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "그록봇", description = "외부 AI 에이전트(그록봇) 대기 문의 조회·답변 수신 API. Authorization: Bearer {봇 토큰} 전용(일반 JWT 불가)")
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class BotReplyController {

    private final BotReplyService botReplyService;
    private final BotDispatchService botDispatchService;

    @Operation(summary = "대기 문의 조회(lease)", description = "Slack 신호를 받으면 호출한다. 조회 가능한 문의를 오래된 순으로 "
            + "limit건(기본 10, 1~20으로 클램프) 가져가고 5분간 답변 권한(lease)을 준다. 5분 안에 답하지 않으면 다시 대기로 돌아가고, "
            + "3회 만료되면 담당자에게 넘어간다. 없으면 items는 빈 배열")
    @GetMapping("/pending")
    public ResponseEntity<ApiResult<BotPendingResponse>> listPending(
            @Parameter(description = "가져갈 최대 건수(1~20)", example = "10") @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResult.success(botDispatchService.listPending(limit)));
    }

    @Operation(summary = "문의 답변 등록", description = "reply: 답변 게시 / handoff: 답변 게시 + 담당자 이관. "
            + "404 dispatch 없음, 403 방·메시지 불일치, 409 이미 처리·더 새 문의·조회(lease) 전이거나 기한 지남·담당자 응대 중, "
            + "422 Idempotency-Key 불일치·본문 길이")
    @PostMapping("/replies")
    public ResponseEntity<ApiResult<Void>> reply(
            @Parameter(description = "eventId와 같은 값") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BotReplyRequest request
    ) {
        botReplyService.reply(idempotencyKey, request);
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
