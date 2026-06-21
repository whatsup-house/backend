package com.whatsuphouse.backend.domain.translation.enums;

// 번역 상태 (KAN-266). PENDING=대기, DONE=완료, FAILED=실패. AI 파이프라인(KAN-267)이 갱신한다.
public enum TranslationStatus {
    PENDING,
    DONE,
    FAILED
}
