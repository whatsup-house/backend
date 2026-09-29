package com.whatsuphouse.backend.domain.ticket.controller;

import com.whatsuphouse.backend.domain.ticket.dto.request.TicketPurchaseRequest;
import com.whatsuphouse.backend.domain.ticket.dto.response.MyTicketsResponse;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketProductResponse;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@Tag(name = "이용권", description = "우연한 식탁 이용권 API")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @Operation(summary = "구매 가능한 이용권 상품 목록")
    @GetMapping("/products")
    public ResponseEntity<ApiResult<List<TicketProductResponse>>> products() {
        return ResponseEntity.ok(ApiResult.success(ticketService.listProducts()));
    }

    @Operation(summary = "이용권 구매(선결제) 요청", description = "입금 확인 전까지 PENDING 상태로 생성된다.")
    @PostMapping("/purchase")
    public ResponseEntity<ApiResult<TicketPassResponse>> purchase(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TicketPurchaseRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success(
                ticketService.purchase(principal.getUserId(), request.getProductId(), request.getApplicationId())));
    }

    @Operation(summary = "내 이용권/잔여 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResult<MyTicketsResponse>> getMyTickets(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID applicationId
    ) {
        return ResponseEntity.ok(ApiResult.success(
                ticketService.getMyTickets(principal.getUserId(), applicationId)));
    }
}
