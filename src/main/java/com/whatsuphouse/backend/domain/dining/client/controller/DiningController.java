package com.whatsuphouse.backend.domain.dining.client.controller;

import com.whatsuphouse.backend.domain.dining.client.dto.request.FeedbackCreateRequest;
import com.whatsuphouse.backend.domain.dining.client.dto.request.SafetyReportCreateRequest;
import com.whatsuphouse.backend.domain.dining.client.dto.response.DiningHistoryResponse;
import com.whatsuphouse.backend.domain.dining.client.service.DiningService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "우연한 식탁 참가 기록", description = "우연한 식탁 테이블 피드백·멤버 신고·참가 이력 API")
@RestController
@RequestMapping("/api/dining")
@RequiredArgsConstructor
public class DiningController {

    private final DiningService diningService;

    @Operation(summary = "테이블 피드백",
            description = "확정·종료 테이블이고 회차 날짜가 지났거나 당일 종료 시각이 지난 뒤에 멤버당 1회 남길 수 있습니다. "
                    + "peers(사람별 선호)는 같은 테이블의 다른 멤버만 한 번씩, 운영자·매칭에만 쓰이고 상대에게 공개되지 않습니다. "
                    + "멤버가 아니면 403, 피드백 가능 상태가 아니거나 peers가 잘못되면 400, 이미 남겼으면 409, 테이블이 없으면 404.")
    @PostMapping("/tables/{id}/feedback")
    public ResponseEntity<ApiResult<Void>> submitFeedback(
            @Parameter(description = "테이블 ID", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7") @PathVariable UUID id,
            @Valid @RequestBody FeedbackCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        diningService.submitFeedback(id, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "테이블 멤버 신고",
            description = "같은 테이블 멤버를 신고합니다. 운영자 예외함에 안전(SAFETY) 건으로 접수되고 두 사람은 이후 같은 테이블에 배정되지 않습니다. "
                    + "멤버가 아니면 403, 자기 자신이나 같은 테이블 멤버가 아닌 대상은 400, 테이블이 없으면 404.")
    @PostMapping("/tables/{id}/reports")
    public ResponseEntity<ApiResult<Void>> reportMember(
            @Parameter(description = "테이블 ID", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7") @PathVariable UUID id,
            @Valid @RequestBody SafetyReportCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        diningService.reportMember(id, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "내 우연한 식탁 참가 이력",
            description = "확정·종료 테이블 멤버십 기준, 최신 회차 순. 취소한 신청은 빠집니다. attendanceStatus는 참석 기록이 없으면 null입니다.")
    @GetMapping("/me/history")
    public ResponseEntity<ApiResult<List<DiningHistoryResponse>>> listMyHistory(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(diningService.listMyHistory(principal.getUserId())));
    }
}
