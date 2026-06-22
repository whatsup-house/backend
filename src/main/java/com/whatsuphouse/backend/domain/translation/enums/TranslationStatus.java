package com.whatsuphouse.backend.domain.translation.enums;

// 번역 상태 (KAN-266). PENDING=대기, DONE=완료, FAILED=실패, SKIPPED=예산초과 등으로 건너뜀.
// AI 파이프라인(KAN-267)이 갱신한다. localizer는 DONE만 노출하므로 그 외 상태는 ko fallback 된다.
public enum TranslationStatus {
    PENDING,
    DONE,
    FAILED,
    SKIPPED
}
