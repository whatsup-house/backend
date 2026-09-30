package com.whatsuphouse.backend.domain.matching.controller;

import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableDetailResponse;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.matching.service.DiningTableDetailService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "우연한 식탁 테이블", description = "참가자 테이블 상세·체크인 API")
@RestController
@RequestMapping("/api/dining/tables")
@RequiredArgsConstructor
public class DiningTableController {

    private final DiningTableDetailService diningTableDetailService;
    private final DiningAttendanceService diningAttendanceService;

    @Operation(summary = "테이블 상세", description = "본인이 멤버인 확정(CONFIRMED)·완료(DONE) 테이블의 회차·식당·채팅방·멤버 소개·취소 정책·내 참석. "
            + "멤버는 회원 ID·닉네임·MBTI·관심사만 보인다(연락처·실명·출생연도 제외). 멤버가 아니거나 확정 전이면 403, 테이블이 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<DiningTableDetailResponse>> getTable(
            @Parameter(description = "테이블 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResult.success(diningTableDetailService.getTable(id, principal.getUserId())));
    }

    @Operation(summary = "체크인", description = "회차 시작 2시간 전부터 2시간 후까지 본인 참석을 ATTENDED로 바꾸고 체크인 시각을 남긴다. "
            + "이미 체크인했으면 그대로 200(멱등). 테이블이 없으면 404, 본인이 멤버인 확정·완료 테이블이 아니면 403, "
            + "시간 밖이면 400(CHECKIN_WINDOW_CLOSED), 취소·노쇼 처리된 참석이면 400(INVALID_STATUS_TRANSITION).")
    @PostMapping("/{id}/check-in")
    public ResponseEntity<ApiResult<DiningTableDetailResponse.AttendanceView>> checkIn(
            @Parameter(description = "테이블 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResult.success(diningAttendanceService.checkIn(id, principal.getUserId())));
    }
}
