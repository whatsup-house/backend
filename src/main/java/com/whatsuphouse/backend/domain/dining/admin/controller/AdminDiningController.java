package com.whatsuphouse.backend.domain.dining.admin.controller;

import com.whatsuphouse.backend.domain.dining.admin.dto.request.ExceptionCaseStatusRequest;
import com.whatsuphouse.backend.domain.dining.admin.dto.request.MatchingRuleRequest;
import com.whatsuphouse.backend.domain.dining.admin.dto.request.SessionVenueRequest;
import com.whatsuphouse.backend.domain.dining.admin.dto.request.TableVenueRequest;
import com.whatsuphouse.backend.domain.dining.admin.dto.request.VenueRequest;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DiningApplicantResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DiningDashboardResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.ExceptionCaseResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.MatchingRuleResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.SessionVenueResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.TableVenueResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.VenueResponse;
import com.whatsuphouse.backend.domain.dining.admin.service.AdminDiningService;
import com.whatsuphouse.backend.domain.dining.admin.service.AdminExceptionCaseService;
import com.whatsuphouse.backend.domain.dining.admin.service.AdminMatchingRuleService;
import com.whatsuphouse.backend.domain.dining.admin.service.AdminVenueService;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Tag(name = "우연한 식탁 운영 (관리자)", description = "운영 대시보드, 회차 신청자·CSV, 예외함, 매칭 규칙, 식당 풀 API")
@RestController
@RequestMapping("/api/admin/dining")
@RequiredArgsConstructor
public class AdminDiningController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AdminDiningService adminDiningService;
    private final AdminExceptionCaseService adminExceptionCaseService;
    private final AdminMatchingRuleService adminMatchingRuleService;
    private final AdminVenueService adminVenueService;

    // ── 대시보드·신청자 ────────────────────────────────────────────────────────

    @Operation(summary = "운영 대시보드", description = "다가오는 우연한 식탁 회차 기준 타일 수치와 회차 카드")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResult<DiningDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResult.success(adminDiningService.getDashboard()));
    }

    @Operation(summary = "회차 신청자 표", description = "이름·나이·성별·MBTI·결제·매칭 상태·희망 회차·참가 횟수·제외 관계. "
            + "?format=csv면 같은 내용을 CSV(UTF-8 BOM)로 내려준다.")
    @GetMapping("/sessions/{id}/applicants")
    public ResponseEntity<ApiResult<List<DiningApplicantResponse>>> listApplicants(
            @Parameter(description = "회차 ID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResult.success(adminDiningService.listApplicants(id)));
    }

    @Operation(summary = "회차 신청자 CSV", description = "신청자 표 CSV(UTF-8 BOM)")
    @GetMapping(value = "/sessions/{id}/applicants", params = "format=csv")
    public ResponseEntity<byte[]> exportApplicantsCsv(@Parameter(description = "회차 ID") @PathVariable UUID id) {
        return csv("applicants-" + id + ".csv", adminDiningService.exportApplicantsCsv(id));
    }

    @Operation(summary = "테이블 결과 CSV", description = "회차의 테이블(매칭 그룹)·멤버 CSV(UTF-8 BOM). 멤버 1명당 1행")
    @GetMapping("/sessions/{id}/tables.csv")
    public ResponseEntity<byte[]> exportTablesCsv(@Parameter(description = "회차 ID") @PathVariable UUID id) {
        return csv("tables-" + id + ".csv", adminDiningService.exportTablesCsv(id));
    }

    // ── 예외함 ────────────────────────────────────────────────────────────────

    @Operation(summary = "예외함 목록", description = "전체 회차 횡단. type·status 미지정 시 전체, 최신 건 우선")
    @GetMapping("/exceptions")
    public ResponseEntity<ApiResult<List<ExceptionCaseResponse>>> listExceptionCases(
            @Parameter(description = "유형", example = "VENUE") @RequestParam(required = false) ExceptionCaseType type,
            @Parameter(description = "상태", example = "OPEN") @RequestParam(required = false) ExceptionCaseStatus status) {
        return ResponseEntity.ok(ApiResult.success(adminExceptionCaseService.listExceptionCases(type, status)));
    }

    @Operation(summary = "예외 처리", description = "RESOLVED는 메모 필수. SAFETY는 action(WARN|RESTRICT|BAN)을 함께 줄 수 있고 "
            + "RESTRICT/BAN이면 신청 회원의 우연한 식탁 참여 자격이 RESTRICTED가 된다. OPEN은 다시 열기.")
    @PatchMapping("/exceptions/{id}")
    public ResponseEntity<ApiResult<ExceptionCaseResponse>> changeExceptionCaseStatus(
            @Parameter(description = "예외 ID") @PathVariable UUID id,
            @Valid @RequestBody ExceptionCaseStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResult.success(
                adminExceptionCaseService.changeExceptionCaseStatus(id, principal.getUserId(), request)));
    }

    // ── 매칭 규칙 ─────────────────────────────────────────────────────────────

    @Operation(summary = "매칭 규칙 조회", description = "회차에 값이 없을 때 쓰는 매칭 규칙 기본값")
    @GetMapping("/settings/matching-rules")
    public ResponseEntity<ApiResult<MatchingRuleResponse>> getMatchingRules() {
        return ResponseEntity.ok(ApiResult.success(adminMatchingRuleService.getMatchingRules()));
    }

    @Operation(summary = "매칭 규칙 수정", description = "전체 교체. 테이블 최소 인원은 최대 인원보다 클 수 없다.")
    @PutMapping("/settings/matching-rules")
    public ResponseEntity<ApiResult<MatchingRuleResponse>> updateMatchingRules(
            @Valid @RequestBody MatchingRuleRequest request) {
        return ResponseEntity.ok(ApiResult.success(adminMatchingRuleService.updateMatchingRules(request)));
    }

    // ── 식당 ──────────────────────────────────────────────────────────────────

    @Operation(summary = "식당 목록", description = "삭제되지 않은 식당, 지역·이름 순")
    @GetMapping("/venues")
    public ResponseEntity<ApiResult<List<VenueResponse>>> listVenues() {
        return ResponseEntity.ok(ApiResult.success(adminVenueService.listVenues()));
    }

    @Operation(summary = "식당 생성")
    @PostMapping("/venues")
    public ResponseEntity<ApiResult<VenueResponse>> createVenue(@Valid @RequestBody VenueRequest request) {
        return ResponseEntity.ok(ApiResult.success(adminVenueService.createVenue(request)));
    }

    @Operation(summary = "식당 수정", description = "전체 교체")
    @PutMapping("/venues/{id}")
    public ResponseEntity<ApiResult<VenueResponse>> updateVenue(
            @Parameter(description = "식당 ID") @PathVariable UUID id,
            @Valid @RequestBody VenueRequest request) {
        return ResponseEntity.ok(ApiResult.success(adminVenueService.updateVenue(id, request)));
    }

    @Operation(summary = "식당 삭제", description = "soft delete. 이미 배정된 회차 풀·테이블은 유지되고 새 배정만 막힌다.")
    @DeleteMapping("/venues/{id}")
    public ResponseEntity<ApiResult<Void>> deleteVenue(@Parameter(description = "식당 ID") @PathVariable UUID id) {
        adminVenueService.deleteVenue(id);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @Operation(summary = "회차 식당 풀 일괄 설정", description = "요청 목록으로 통째로 교체. 배정된 테이블 수보다 작게 줄이거나 "
            + "배정 중인 식당을 빼면 409, 새로 넣는 비활성 식당은 400.")
    @PutMapping("/sessions/{id}/venues")
    public ResponseEntity<ApiResult<List<SessionVenueResponse>>> updateSessionVenues(
            @Parameter(description = "회차 ID") @PathVariable UUID id,
            @Valid @RequestBody List<SessionVenueRequest> request) {
        return ResponseEntity.ok(ApiResult.success(adminVenueService.updateSessionVenues(id, request)));
    }

    @Operation(summary = "테이블 식당 배정", description = "회차 식당 풀에 있는 활성 식당만. 수용 테이블이 가득 차면 409.")
    @PutMapping("/tables/{id}/venue")
    public ResponseEntity<ApiResult<TableVenueResponse>> assignTableVenue(
            @Parameter(description = "테이블 ID(현재는 매칭 그룹 ID)") @PathVariable UUID id,
            @Valid @RequestBody TableVenueRequest request) {
        return ResponseEntity.ok(ApiResult.success(adminVenueService.assignTableVenue(id, request.getVenueId())));
    }

    private static ResponseEntity<byte[]> csv(String filename, byte[] body) {
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
