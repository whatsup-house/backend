package com.whatsuphouse.backend.domain.mailtemplate.admin.controller;

import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.request.MailTemplatePreviewRequest;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.request.MailTemplateUpdateRequest;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response.MailTemplateDetailResponse;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response.MailTemplatePreviewResponse;
import com.whatsuphouse.backend.domain.mailtemplate.admin.dto.response.MailTemplateResponse;
import com.whatsuphouse.backend.domain.mailtemplate.admin.service.AdminMailTemplateService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "관리자 - 메일 템플릿", description = "알림 메일 제목/본문 관리 API")
@RestController
@RequestMapping("/api/admin/mail-templates")
@RequiredArgsConstructor
public class AdminMailTemplateController {

    private final AdminMailTemplateService adminMailTemplateService;

    @Operation(summary = "메일 템플릿 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResult<List<MailTemplateResponse>>> getTemplates() {
        return ResponseEntity.ok(ApiResult.success(adminMailTemplateService.getTemplates()));
    }

    @Operation(summary = "메일 템플릿 상세 조회", description = "사용 가능한 치환 변수 목록을 함께 반환한다.")
    @GetMapping("/{templateKey}")
    public ResponseEntity<ApiResult<MailTemplateDetailResponse>> getTemplate(
            @Parameter(description = "템플릿 키 (예: APPLICATION_PENDING)") @PathVariable String templateKey
    ) {
        return ResponseEntity.ok(ApiResult.success(adminMailTemplateService.getTemplate(templateKey)));
    }

    @Operation(summary = "메일 템플릿 수정", description = "제목/본문을 운영 중 수정한다.")
    @PutMapping("/{templateKey}")
    public ResponseEntity<ApiResult<MailTemplateDetailResponse>> updateTemplate(
            @PathVariable String templateKey,
            @Valid @RequestBody MailTemplateUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success(adminMailTemplateService.updateTemplate(templateKey, request)));
    }

    @Operation(summary = "메일 템플릿 미리보기", description = "초안 제목/본문과 변수로 치환 결과를 미리본다.")
    @PostMapping("/{templateKey}/preview")
    public ResponseEntity<ApiResult<MailTemplatePreviewResponse>> preview(
            @PathVariable String templateKey,
            @RequestBody(required = false) MailTemplatePreviewRequest request
    ) {
        MailTemplatePreviewRequest body = request != null ? request : new MailTemplatePreviewRequest();
        return ResponseEntity.ok(ApiResult.success(adminMailTemplateService.preview(templateKey, body)));
    }
}
