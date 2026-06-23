package com.whatsuphouse.backend.domain.participant.client.controller;

import com.whatsuphouse.backend.domain.participant.client.dto.GuestOverviewResponse;
import com.whatsuphouse.backend.domain.participant.client.service.GuestOverviewService;
import com.whatsuphouse.backend.global.common.ApiResult;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
public class GuestOverviewController {
    private final GuestOverviewService guestOverviewService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResult<GuestOverviewResponse>> overview(
            @RequestParam @Pattern(regexp = "^01\\d{8,9}$") String phone,
            @RequestParam @Email String email) {
        return ResponseEntity.ok(ApiResult.success(guestOverviewService.getOverview(phone, email)));
    }
}
