package com.whatsuphouse.backend.domain.translation.event;

import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * 콘텐츠 생성/수정 시 발행되는 자동 번역 요청 이벤트. (KAN-267)
 * koFields는 (필드명 → 한국어 원문) 맵이며, 리스너가 커밋 후 비동기로 번역을 시작한다.
 */
@Getter
@RequiredArgsConstructor
public class ContentTranslationRequestedEvent {

    private final TranslatableType type;
    private final UUID entityId;
    private final Map<String, String> koFields;
}
