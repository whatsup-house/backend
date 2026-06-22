package com.whatsuphouse.backend.domain.ticket.admin.controller;

import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminTicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.admin.dto.request.TicketAdjustmentRequest;
import com.whatsuphouse.backend.domain.ticket.admin.dto.response.TicketTransactionResponse;
import com.whatsuphouse.backend.domain.ticket.admin.service.AdminTicketService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@Tag(name = "관리자 이용권", description = "이용권 입금 확인 API")
@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
public class AdminTicketController {

    private final AdminTicketService adminTicketService;

    @Operation(summary = "입금 대기(PENDING) 이용권 목록")
    @GetMapping("/pending")
    public ResponseEntity<ApiResult<List<AdminTicketPassResponse>>> listPending() {
        return ResponseEntity.ok(ApiResult.success(adminTicketService.listPending()));
    }

    @Operation(summary = "이용권 입금 확인(활성화)")
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<ApiResult<AdminTicketPassResponse>> confirm(
            @Parameter(description = "이용권 ID", example = "b3f1c2d4-...") @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResult.success(adminTicketService.confirm(id)));
    }

    @Operation(summary = "이용권 잔여 횟수 수동 추가·회수")
    @PatchMapping("/{id}/adjust")
    public ResponseEntity<ApiResult<AdminTicketPassResponse>> adjust(
            @PathVariable UUID id, @Valid @RequestBody TicketAdjustmentRequest request) {
        return ResponseEntity.ok(ApiResult.success(adminTicketService.adjust(id, request)));
    }

    @Operation(summary = "이용권 거래내역 조회")
    @GetMapping("/{id}/transactions")
    public ResponseEntity<ApiResult<List<TicketTransactionResponse>>> transactions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResult.success(adminTicketService.getTransactions(id)));
    }
}
