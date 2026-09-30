package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.dining.admin.dto.request.ExceptionCaseStatusRequest;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.ExceptionCaseResponse;
import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.enums.SafetyAction;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 예외함 조회·처리. 예외 생성은 매칭·확정 파이프라인(KAN-345)이 한다. (KAN-348) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminExceptionCaseService {

    private final ExceptionCaseRepository exceptionCaseRepository;
    private final AdminApplicationService adminApplicationService;
    private final UserService userService;

    /** 전체 회차 횡단. type/status가 없으면 그 조건은 걸지 않는다. 최신 건 우선. */
    public List<ExceptionCaseResponse> listExceptionCases(ExceptionCaseType type, ExceptionCaseStatus status) {
        return exceptionCaseRepository.findAllByFilter(type, status).stream()
                .map(ExceptionCaseResponse::from)
                .toList();
    }

    /**
     * RESOLVED: 메모 필수. SAFETY는 조치를 함께 줄 수 있고 RESTRICT/BAN이면 신청 회원의 우연한 식탁 참여를 RESTRICTED로 바꾼다.
     * OPEN: 다시 열기(조치는 줄 수 없다).
     */
    @Transactional
    public ExceptionCaseResponse changeExceptionCaseStatus(UUID id, UUID adminId, ExceptionCaseStatusRequest request) {
        ExceptionCase exceptionCase = exceptionCaseRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.EXCEPTION_CASE_NOT_FOUND));

        if (request.getStatus() == ExceptionCaseStatus.OPEN) {
            if (request.getAction() != null) {
                throw new CustomException(ErrorCode.EXCEPTION_ACTION_NOT_ALLOWED);
            }
            exceptionCase.reopen();
            return ExceptionCaseResponse.from(exceptionCase);
        }

        SafetyAction action = request.getAction();
        exceptionCase.resolve(adminId, request.getNote(), action);
        if (action != null && action.restrictsRandomTable()) {
            userService.restrictRandomTable(findTargetUserId(exceptionCase));
        }
        return ExceptionCaseResponse.from(exceptionCase);
    }

    private UUID findTargetUserId(ExceptionCase exceptionCase) {
        if (exceptionCase.getApplicationId() == null) {
            throw new CustomException(ErrorCode.EXCEPTION_ACTION_TARGET_MISSING);
        }
        return adminApplicationService.findApplicantUserId(exceptionCase.getApplicationId())
                .orElseThrow(() -> new CustomException(ErrorCode.EXCEPTION_ACTION_TARGET_MISSING));
    }
}
