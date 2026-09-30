package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.dining.admin.dto.request.ExceptionCaseStatusRequest;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.ExceptionCaseResponse;
import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.entity.SafetyReport;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.enums.SafetyAction;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import com.whatsuphouse.backend.domain.dining.repository.SafetyReportRepository;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AdminExceptionCaseServiceTest {

    @Mock
    private ExceptionCaseRepository exceptionCaseRepository;

    @Mock
    private SafetyReportRepository safetyReportRepository;

    @Mock
    private AdminApplicationService adminApplicationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminExceptionCaseService adminExceptionCaseService;

    private final UUID adminId = UUID.randomUUID();

    private ExceptionCase givenCase(ExceptionCaseType type, UUID applicationId) {
        ExceptionCase exceptionCase = ExceptionCase.open(type, null, null, applicationId, "사유");
        ReflectionTestUtils.setField(exceptionCase, "id", UUID.randomUUID());
        given(exceptionCaseRepository.findById(exceptionCase.getId())).willReturn(Optional.of(exceptionCase));
        return exceptionCase;
    }

    private ExceptionCaseResponse resolve(ExceptionCase exceptionCase, String note, SafetyAction action) {
        return adminExceptionCaseService.changeExceptionCaseStatus(exceptionCase.getId(), adminId,
                new ExceptionCaseStatusRequest(ExceptionCaseStatus.RESOLVED, note, action));
    }

    @Test
    @DisplayName("처리 메모 없이 RESOLVED로 바꾸면 400이고 상태는 그대로다")
    void resolve_withoutNote_throws() {
        ExceptionCase exceptionCase = givenCase(ExceptionCaseType.VENUE, null);

        assertThatThrownBy(() -> resolve(exceptionCase, "  ", null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCEPTION_NOTE_REQUIRED);
        assertThat(exceptionCase.getStatus()).isEqualTo(ExceptionCaseStatus.OPEN);
    }

    @Test
    @DisplayName("SAFETY를 RESTRICT로 처리하면 신청 회원의 우연한 식탁 참여를 제한하고 조치를 기록한다")
    void resolveSafety_restrict_restrictsApplicant() {
        UUID applicationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ExceptionCase exceptionCase = givenCase(ExceptionCaseType.SAFETY, applicationId);
        given(adminApplicationService.findApplicantUserId(applicationId)).willReturn(Optional.of(userId));

        ExceptionCaseResponse response = resolve(exceptionCase, " 반복 신고 확인 ", SafetyAction.RESTRICT);

        then(userService).should().restrictRandomTable(userId);
        assertThat(response.getStatus()).isEqualTo(ExceptionCaseStatus.RESOLVED);
        assertThat(response.getAction()).isEqualTo(SafetyAction.RESTRICT);
        assertThat(response.getResolvedBy()).isEqualTo(adminId);
        assertThat(response.getResolutionNote()).isEqualTo("반복 신고 확인");
        assertThat(response.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("신고로 생긴 SAFETY 건을 처리·다시 열면 연결된 신고도 상태·조치·메모를 따라간다")
    void resolveSafety_syncsSafetyReport() {
        ExceptionCase exceptionCase = givenCase(ExceptionCaseType.SAFETY, UUID.randomUUID());
        SafetyReport report = new SafetyReport(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "사유",
                exceptionCase.getId());
        given(safetyReportRepository.findByExceptionCaseId(exceptionCase.getId())).willReturn(Optional.of(report));

        resolve(exceptionCase, "경고 안내", SafetyAction.WARN);

        assertThat(report.getStatus()).isEqualTo(ExceptionCaseStatus.RESOLVED);
        assertThat(report.getAction()).isEqualTo(SafetyAction.WARN);
        assertThat(report.getAdminNote()).isEqualTo("경고 안내");
        assertThat(report.getResolvedAt()).isNotNull();

        adminExceptionCaseService.changeExceptionCaseStatus(exceptionCase.getId(), adminId,
                new ExceptionCaseStatusRequest(ExceptionCaseStatus.OPEN, null, null));

        assertThat(report.getStatus()).isEqualTo(ExceptionCaseStatus.OPEN);
        assertThat(report.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("SAFETY를 WARN으로 처리하면 조치만 기록하고 참여 자격은 건드리지 않는다")
    void resolveSafety_warn_onlyRecords() {
        ExceptionCase exceptionCase = givenCase(ExceptionCaseType.SAFETY, UUID.randomUUID());

        ExceptionCaseResponse response = resolve(exceptionCase, "경고 안내", SafetyAction.WARN);

        then(userService).shouldHaveNoInteractions();
        assertThat(response.getAction()).isEqualTo(SafetyAction.WARN);
    }

    @Test
    @DisplayName("SAFETY가 아닌 예외에 조치를 주면 400")
    void resolveNonSafety_withAction_throws() {
        ExceptionCase exceptionCase = givenCase(ExceptionCaseType.PAYMENT, UUID.randomUUID());

        assertThatThrownBy(() -> resolve(exceptionCase, "메모", SafetyAction.BAN))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCEPTION_ACTION_NOT_ALLOWED);
        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("신청이 연결되지 않은 SAFETY 예외에 BAN을 주면 400")
    void resolveSafety_banWithoutApplication_throws() {
        ExceptionCase exceptionCase = givenCase(ExceptionCaseType.SAFETY, null);

        assertThatThrownBy(() -> resolve(exceptionCase, "메모", SafetyAction.BAN))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXCEPTION_ACTION_TARGET_MISSING);
        then(userService).shouldHaveNoInteractions();
    }
}
