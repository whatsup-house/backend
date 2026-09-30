package com.whatsuphouse.backend.domain.dining.service;

import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 다른 도메인·흐름이 예외함에 건을 올릴 때 쓰는 진입점. 조회·처리는 AdminExceptionCaseService. */
@Service
@Transactional
@RequiredArgsConstructor
public class ExceptionCaseService {

    private final ExceptionCaseRepository exceptionCaseRepository;

    public ExceptionCase open(ExceptionCaseType type, UUID sessionId, UUID tableId, UUID applicationId, String reason) {
        return exceptionCaseRepository.save(ExceptionCase.open(type, sessionId, tableId, applicationId, reason));
    }
}
