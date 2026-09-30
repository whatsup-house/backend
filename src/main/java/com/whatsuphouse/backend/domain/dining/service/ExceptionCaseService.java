package com.whatsuphouse.backend.domain.dining.service;

import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 다른 도메인(매칭·확정 파이프라인)이 예외함에 건을 쌓고 닫는 창구. 조회·운영자 처리는 AdminExceptionCaseService. */
@Service
@RequiredArgsConstructor
public class ExceptionCaseService {

    private final ExceptionCaseRepository exceptionCaseRepository;

    @Transactional
    public ExceptionCase open(ExceptionCaseType type, UUID sessionId, UUID tableId, UUID applicationId, String reason) {
        return exceptionCaseRepository.save(ExceptionCase.open(type, sessionId, tableId, applicationId, reason));
    }

    /** 이 테이블의 OPEN 예외 중 유형이 type이고 사유가 reasonPrefix로 시작하는 건. */
    @Transactional(readOnly = true)
    public List<ExceptionCase> listOpen(ExceptionCaseType type, UUID tableId, String reasonPrefix) {
        return exceptionCaseRepository.findAllByTypeAndTableIdAndStatus(type, tableId, ExceptionCaseStatus.OPEN).stream()
                .filter(exceptionCase -> exceptionCase.getReason().startsWith(reasonPrefix))
                .toList();
    }

    /** listOpen과 같은 조건의 건을 처리 완료로 닫는다. 재시도 성공 등 자동 해소용. */
    @Transactional
    public void resolveOpen(ExceptionCaseType type, UUID tableId, String reasonPrefix, UUID resolvedBy, String note) {
        listOpen(type, tableId, reasonPrefix).forEach(exceptionCase -> exceptionCase.resolve(resolvedBy, note, null));
    }
}
