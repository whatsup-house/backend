package com.whatsuphouse.backend.domain.matching.controller;

import com.whatsuphouse.backend.domain.matching.dto.request.ForceAssignRequest;
import com.whatsuphouse.backend.domain.matching.dto.request.MemberMoveRequest;
import com.whatsuphouse.backend.domain.matching.dto.request.RestaurantRequest;
import com.whatsuphouse.backend.domain.matching.service.DiningTableService;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "자동매칭 검토 (관리자)", description = "매칭 그룹/멤버 수동 조정 및 확정 API")
@RestController
@RequestMapping("/api/admin/matching")
@RequiredArgsConstructor
public class MatchingGroupController {

    private final MatchingService matchingService;
    private final DiningTableService diningTableService;

    @Operation(summary = "멤버 이동", description = "매칭 멤버를 같은 회차 다른 그룹으로 이동합니다 (수동 배정 처리). "
            + "신규 조정 API와 같은 검증(같은 회차, 하드 조건 재검증 → 위반 시 400 TABLE_RULE_VIOLATION)을 거치며, "
            + "사유를 받지 않으므로 확정 그룹은 조정할 수 없습니다(400). 신규 API는 POST /api/admin/dining/tables/{id}/members/{memberId}/move.")
    @PatchMapping("/members/{memberId}/move")
    public ResponseEntity<ApiResult<Void>> moveMember(
            @PathVariable UUID memberId,
            @Valid @RequestBody MemberMoveRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        diningTableService.moveMember(memberId, request.getTargetGroupId(), principal.getUserId());
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "멤버 제외", description = "매칭 멤버를 그룹에서 제외합니다 (미배정 상태로 돌아감).")
    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<ApiResult<Void>> excludeMember(@PathVariable UUID memberId) {
        matchingService.excludeMember(memberId);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "강제 배정", description = "미배정 신청을 특정 그룹에 강제 배정합니다. 이 회차의 결제 완료 신청만(아니면 400), "
            + "하드 조건 재검증 위반 시 400 TABLE_RULE_VIOLATION, 확정 그룹은 조정할 수 없습니다(400).")
    @PostMapping("/groups/{groupId}/members")
    public ResponseEntity<ApiResult<Void>> assignMember(
            @PathVariable UUID groupId,
            @Valid @RequestBody ForceAssignRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        diningTableService.assignMember(groupId, request.getApplicationId(), null, principal.getUserId());
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "그룹 확정", description = "매칭 그룹을 확정(CONFIRMED) 처리합니다.")
    @PatchMapping("/groups/{groupId}/confirm")
    public ResponseEntity<ApiResult<Void>> confirmGroup(@PathVariable UUID groupId) {
        matchingService.confirmGroup(groupId);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "식당 정보 입력", description = "매칭 그룹의 식당명/주소를 입력합니다.")
    @PatchMapping("/groups/{groupId}/restaurant")
    public ResponseEntity<ApiResult<Void>> updateRestaurant(
            @PathVariable UUID groupId,
            @RequestBody RestaurantRequest request) {
        matchingService.updateRestaurant(groupId, request.getRestaurantName(), request.getRestaurantAddress());
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
