package com.whatsuphouse.backend.domain.form.client.controller;

import com.whatsuphouse.backend.domain.form.client.dto.response.GatheringFormResponse;
import com.whatsuphouse.backend.domain.form.client.service.FormService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "신청폼", description = "신청폼 조회 API")
@RestController
@RequestMapping("/api/gatherings")
@RequiredArgsConstructor
public class FormController {

    private final FormService formService;

    @Operation(summary = "신청폼 조회", description = "게더링의 활성 신청폼과 질문 목록을 조회합니다.")
    @GetMapping("/{gatheringId}/form")
    public ResponseEntity<ApiResult<GatheringFormResponse>> getForm(
            @PathVariable UUID gatheringId
    ) {
        return ResponseEntity.ok(ApiResult.success(formService.getForm(gatheringId)));
    }
}
