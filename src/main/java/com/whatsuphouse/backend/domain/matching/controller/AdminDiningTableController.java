package com.whatsuphouse.backend.domain.matching.controller;

import com.whatsuphouse.backend.domain.matching.dto.request.DiningTableDissolveRequest;
import com.whatsuphouse.backend.domain.matching.dto.request.DiningTableMergeRequest;
import com.whatsuphouse.backend.domain.matching.dto.request.DiningTableMoveRequest;
import com.whatsuphouse.backend.domain.matching.dto.request.DiningTableSplitRequest;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableAdjustResponse;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableExplanationResponse;
import com.whatsuphouse.backend.domain.matching.service.DiningTableExplanationService;
import com.whatsuphouse.backend.domain.matching.service.DiningTableService;
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

import java.util.UUID;

@Tag(name = "우연한 식탁 테이블 조정 (관리자)", description = "테이블 매칭 근거 조회, 멤버 이동·분리·병합·해체")
@RestController
@RequestMapping("/api/admin/dining/tables")
@RequiredArgsConstructor
public class AdminDiningTableController {

    // 모든 조정의 공통 규칙(Swagger 설명)
    private static final String RULES = " 같은 회차의 제안·확정 테이블끼리만(아니면 400). 조정 후 관련 테이블을 하드 조건(인원·나이 차·제외 관계)으로 "
            + "재검증해 위반이 있으면 400 TABLE_RULE_VIOLATION(params.violations)으로 되돌리고, 통과하면 점수를 다시 매기고 잠근다(재실행 보존). "
            + "확정 테이블이 끼면 사유 필수(400)이며 채팅방 멤버도 맞춘다. 테이블·멤버가 없으면 404.";

    private final DiningTableService diningTableService;
    private final DiningTableExplanationService diningTableExplanationService;

    @Operation(summary = "매칭 근거", description = "현재 멤버로 매칭 엔진을 다시 계산한 그룹 점수·점수 내역(0보다 큰 페널티만)·항목별 쌍 평균·장점 문구, "
            + "멤버(직업 캐릭터·출생연도·성별·MBTI·신청서 응답 원문, 이름·연락처·이메일 제외), 모든 쌍의 점수와 항목 값"
            + "(gender, mbti, interests, wantedStyle, custom 중 값 있는 것). 저장값이 아니라 조회 시점 계산이다. 테이블이 없으면 404.")
    @GetMapping("/{id}/explanation")
    public ResponseEntity<ApiResult<DiningTableExplanationResponse>> getExplanation(
            @Parameter(description = "테이블 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResult.success(diningTableExplanationService.getExplanation(id)));
    }

    @Operation(summary = "멤버 이동", description = "멤버를 같은 회차 다른 테이블로 옮긴다." + RULES)
    @PostMapping("/{id}/members/{memberId}/move")
    public ResponseEntity<ApiResult<DiningTableAdjustResponse>> moveMember(
            @Parameter(description = "원래 테이블 ID") @PathVariable UUID id,
            @Parameter(description = "테이블 멤버 ID") @PathVariable UUID memberId,
            @Valid @RequestBody DiningTableMoveRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResult.success(diningTableService.moveMember(
                id, memberId, request.getTargetTableId(), request.getReason(), principal.getUserId())));
    }

    @Operation(summary = "테이블 분리", description = "고른 멤버를 새 제안(PROPOSED) 테이블로 떼어 낸다. 자동 확정 시각은 원본과 같다." + RULES)
    @PostMapping("/{id}/split")
    public ResponseEntity<ApiResult<DiningTableAdjustResponse>> splitTable(
            @Parameter(description = "테이블 ID") @PathVariable UUID id,
            @Valid @RequestBody DiningTableSplitRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResult.success(diningTableService.splitTable(
                id, request.getMemberIds(), request.getReason(), principal.getUserId())));
    }

    @Operation(summary = "테이블 병합", description = "첫 테이블로 나머지 테이블의 멤버를 합치고 나머지는 해체한다." + RULES)
    @PostMapping("/merge")
    public ResponseEntity<ApiResult<DiningTableAdjustResponse>> mergeTables(
            @Valid @RequestBody DiningTableMergeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResult.success(diningTableService.mergeTables(
                request.getTableIds(), request.getReason(), principal.getUserId())));
    }

    @Operation(summary = "테이블 해체", description = "테이블을 해체(식당 배정 해제)하고 멤버를 재배치 대기(REALLOCATING)로 돌린다." + RULES)
    @PostMapping("/{id}/dissolve")
    public ResponseEntity<ApiResult<DiningTableAdjustResponse>> dissolveTable(
            @Parameter(description = "테이블 ID") @PathVariable UUID id,
            @Valid @RequestBody(required = false) DiningTableDissolveRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResult.success(diningTableService.dissolveTable(
                id, request != null ? request.getReason() : null, principal.getUserId())));
    }
}
