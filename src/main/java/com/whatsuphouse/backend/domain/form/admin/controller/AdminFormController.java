package com.whatsuphouse.backend.domain.form.admin.controller;

import com.whatsuphouse.backend.domain.form.admin.dto.request.FormQuestionCreateRequest;
import com.whatsuphouse.backend.domain.form.admin.dto.request.FormQuestionUpdateRequest;
import com.whatsuphouse.backend.domain.form.admin.dto.response.FormQuestionResponse;
import com.whatsuphouse.backend.domain.form.admin.service.AdminFormService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "신청폼 관리 (관리자)", description = "관리자 신청폼 질문 추가/수정/삭제 API")
@RestController
@RequiredArgsConstructor
public class AdminFormController {

    private final AdminFormService adminFormService;

    @Operation(summary = "질문 목록 조회", description = "게더링 신청폼의 질문 목록을 매칭 설정과 함께 조회합니다. (관리자용)")
    @GetMapping("/api/admin/gatherings/{gatheringId}/form/questions")
    public ResponseEntity<ApiResult<List<FormQuestionResponse>>> getQuestions(
            @PathVariable UUID gatheringId
    ) {
        return ResponseEntity.ok(ApiResult.success(adminFormService.getQuestions(gatheringId)));
    }

    @Operation(summary = "질문 추가", description = "게더링 신청폼에 질문을 추가합니다. 활성 폼이 없으면 자동 생성됩니다.")
    @PostMapping("/api/admin/gatherings/{gatheringId}/form/questions")
    public ResponseEntity<ApiResult<FormQuestionResponse>> addQuestion(
            @PathVariable UUID gatheringId,
            @Valid @RequestBody FormQuestionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success("질문이 추가되었습니다.", adminFormService.addQuestion(gatheringId, request)));
    }

    @Operation(summary = "질문 수정", description = "신청폼 질문을 수정합니다.")
    @PutMapping("/api/admin/form/questions/{questionId}")
    public ResponseEntity<ApiResult<FormQuestionResponse>> updateQuestion(
            @PathVariable UUID questionId,
            @Valid @RequestBody FormQuestionUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success("질문이 수정되었습니다.", adminFormService.updateQuestion(questionId, request)));
    }

    @Operation(summary = "질문 삭제", description = "신청폼 질문을 soft delete 합니다.")
    @DeleteMapping("/api/admin/form/questions/{questionId}")
    public ResponseEntity<ApiResult<Void>> deleteQuestion(
            @PathVariable UUID questionId
    ) {
        adminFormService.deleteQuestion(questionId);
        return ResponseEntity.ok(ApiResult.success("질문이 삭제되었습니다.", null));
    }
}
