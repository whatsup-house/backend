package com.whatsuphouse.backend.domain.ticket.admin.controller;

import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminPendingDepositResponse;
import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminTicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.admin.dto.request.TicketProductRequest;
import com.whatsuphouse.backend.domain.ticket.admin.dto.request.TicketAdjustmentRequest;
import com.whatsuphouse.backend.domain.ticket.admin.dto.response.TicketTransactionResponse;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketProductResponse;
import com.whatsuphouse.backend.domain.ticket.admin.service.AdminTicketService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @Operation(summary = "이용권 상품 목록")
    @GetMapping("/products")
    public ResponseEntity<ApiResult<List<TicketProductResponse>>> products() {
        return ResponseEntity.ok(ApiResult.success(adminTicketService.listProducts()));
    }

    @Operation(summary = "이용권 상품 추가")
    @PostMapping("/products")
    public ResponseEntity<ApiResult<TicketProductResponse>> createProduct(
            @Valid @RequestBody TicketProductRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success(adminTicketService.createProduct(request)));
    }

    @Operation(summary = "이용권 상품 수정")
    @PutMapping("/products/{id}")
    public ResponseEntity<ApiResult<TicketProductResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody TicketProductRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success(adminTicketService.updateProduct(id, request)));
    }

    @Operation(summary = "이용권 상품 삭제")
    @DeleteMapping("/products/{id}")
    public ResponseEntity<ApiResult<Void>> deleteProduct(@PathVariable UUID id) {
        adminTicketService.deleteProduct(id);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "입금 대기(PENDING) 이용권 목록")
    @GetMapping("/pending")
    public ResponseEntity<ApiResult<List<AdminTicketPassResponse>>> listPending() {
        return ResponseEntity.ok(ApiResult.success(adminTicketService.listPending()));
    }

    @Operation(summary = "입금 대기 큐(신청자·게더링 포함, 요청 오래된 순)",
            description = "게더링을 가로질러 입금 확인이 필요한 요청을 시간순으로 보여준다. 행의 ticketPassId로 입금 확인한다.")
    @GetMapping("/deposits/pending")
    public ResponseEntity<ApiResult<List<AdminPendingDepositResponse>>> pendingDeposits() {
        return ResponseEntity.ok(ApiResult.success(adminTicketService.listPendingDeposits()));
    }

    @Operation(summary = "입금 대기 건수(대시보드 뱃지)")
    @GetMapping("/deposits/count")
    public ResponseEntity<ApiResult<Long>> pendingDepositCount() {
        return ResponseEntity.ok(ApiResult.success(adminTicketService.countPendingDeposits()));
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
