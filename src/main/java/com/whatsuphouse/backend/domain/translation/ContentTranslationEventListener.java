package com.whatsuphouse.backend.domain.translation;

import com.whatsuphouse.backend.domain.translation.event.ContentTranslationRequestedEvent;
import com.whatsuphouse.backend.domain.translation.service.AutoTranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 콘텐츠 생성/수정 이벤트를 받아 자동 번역으로 연결하는 리스너. (KAN-267)
 *
 * AFTER_COMMIT으로 처리하는 이유: 콘텐츠 저장 트랜잭션이 커밋된 뒤에만 번역을 시작해야
 * 롤백된 콘텐츠에 대한 번역이 남지 않는다. 실제 번역 호출은 AutoTranslationService가 @Async로 수행한다.
 */
@Component
@RequiredArgsConstructor
public class ContentTranslationEventListener {

    private final AutoTranslationService autoTranslationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContentTranslationRequested(ContentTranslationRequestedEvent event) {
        autoTranslationService.translate(event.getType(), event.getEntityId(), event.getKoFields());
    }
}
