package com.whatsuphouse.backend.domain.dining.enums;

/** SAFETY 예외 처리 조치. WARN은 기록만, RESTRICT/BAN은 우연한 식탁 참여 자격을 RESTRICTED로 바꾼다. */
public enum SafetyAction {
    WARN, RESTRICT, BAN;

    public boolean restrictsRandomTable() {
        return this != WARN;
    }
}
