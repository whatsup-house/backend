package com.whatsuphouse.backend.domain.translation.admin.controller;

import com.whatsuphouse.backend.domain.translation.admin.dto.request.AdminTranslationOverrideRequest;
import com.whatsuphouse.backend.domain.translation.admin.dto.response.AdminTranslationResponse;
import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import com.whatsuphouse.backend.domain.translation.service.ContentTranslationService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "관리자 번역", description = "콘텐츠 번역 조회·수동 보정 API (관리자)")
@RestController
@RequestMapping("/api/admin/translations")
@RequiredArgsConstructor
public class AdminTranslationController {

    private final ContentTranslationService contentTranslationService;

    @Operation(summary = "콘텐츠 번역 목록 조회", description = "엔티티의 필드·로케일별 번역과 상태를 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResult<List<AdminTranslationResponse>>> getTranslations(
            @RequestParam TranslatableType entityType,
            @RequestParam UUID entityId
    ) {
        List<AdminTranslationResponse> result = contentTranslationService.getTranslations(entityType, entityId)
                .stream()
                .map(AdminTranslationResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResult.success(result));
    }

    @Operation(summary = "콘텐츠 번역 수동 보정",
            description = "번역을 직접 수정/확정한다. 저장된 번역은 자동 재번역으로 덮어쓰지 않는다(override).")
    @PutMapping
    public ResponseEntity<ApiResult<AdminTranslationResponse>> override(
            @Valid @RequestBody AdminTranslationOverrideRequest request
    ) {
        AdminTranslationResponse result = AdminTranslationResponse.from(
                contentTranslationService.override(
                        request.getEntityType(),
                        request.getEntityId(),
                        request.getField(),
                        request.getLocale(),
                        request.getValue()));
        return ResponseEntity.ok(ApiResult.success(result));
    }
}
