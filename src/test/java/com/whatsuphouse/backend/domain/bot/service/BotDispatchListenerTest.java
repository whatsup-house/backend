package com.whatsuphouse.backend.domain.bot.service;

import com.whatsuphouse.backend.domain.chat.event.ChatMessageCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class BotDispatchListenerTest {

    @Mock
    private BotDispatchService botDispatchService;

    @InjectMocks
    private BotDispatchListener botDispatchListener;

    @Test
    @DisplayName("방당 PENDING 1건 유니크와 겹치면 새 트랜잭션으로 한 번 더 넣는다(그 PENDING에 디바운스로 묶임)")
    void onMessageCreated_pendingUniqueConflict_retriesOnce() {
        // given
        UUID messageId = UUID.randomUUID();
        willThrow(new DataIntegrityViolationException("uk_bot_dispatch_pending_conversation"))
                .willDoNothing()
                .given(botDispatchService).enqueue(messageId);

        // when
        botDispatchListener.onMessageCreated(new ChatMessageCreatedEvent(UUID.randomUUID(), messageId));

        // then
        then(botDispatchService).should(times(2)).enqueue(messageId);
    }
}
