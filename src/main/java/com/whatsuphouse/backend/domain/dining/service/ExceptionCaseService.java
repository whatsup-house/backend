package com.whatsuphouse.backend.domain.dining.service;

import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 다른 도메인(매칭·환불)이 자동 처리하지 못한 건을 예외함에 올리는 창구. (설계 4.10) */
@Service
@RequiredArgsConstructor
public class ExceptionCaseService {

    private final ExceptionCaseRepository exceptionCaseRepository;

    @Transactional
    public ExceptionCase open(ExceptionCaseType type, UUID sessionId, UUID tableId, UUID applicationId, String reason) {
        return exceptionCaseRepository.save(ExceptionCase.open(type, sessionId, tableId, applicationId, reason));
    }
}
