package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.domain.chat.event.ChatMessageCreatedEvent;
import com.whatsuphouse.backend.domain.chat.service.LinkPreviewFetcher.LinkPreview;
import com.whatsuphouse.backend.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;
import java.util.Optional;

/**
 * 링크 미리보기(docs/chat-design.md 5절). 메시지 생성이 커밋된 뒤 별도 스레드에서 본문 첫 URL의 OG를 조회해 저장하고
 * MESSAGE_UPDATED로 알린다(ChatService.attachLinkPreview → ChatSocketEventListener).
 * URL 단위 캐시: 성공 1시간, 실패 10분(같은 링크를 반복해 보내도 외부 요청은 TTL당 한 번).
 */
@Service
@RequiredArgsConstructor
public class ChatLinkPreviewService {

    private final ChatService chatService;
    private final LinkPreviewFetcher linkPreviewFetcher;
    private final CacheManager cacheManager;

    // 외부 요청(hop당 최대 6초)은 트랜잭션 밖에서 하고, 저장만 attachLinkPreview 트랜잭션에서 한다.
    @Async("linkPreviewExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(ChatMessageCreatedEvent event) {
        chatService.getMessageText(event.getMessageId())
                .flatMap(LinkPreviewFetcher::extractFirstUrl)
                .flatMap(this::findPreview)
                .ifPresent(preview -> chatService.attachLinkPreview(event.getMessageId(), preview.toMap()));
    }

    private Optional<LinkPreview> findPreview(String url) {
        Cache hits = Objects.requireNonNull(cacheManager.getCache(CacheConfig.LINK_PREVIEW_CACHE));
        Cache failures = Objects.requireNonNull(cacheManager.getCache(CacheConfig.LINK_PREVIEW_FAILURE_CACHE));
        LinkPreview cached = hits.get(url, LinkPreview.class);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (failures.get(url) != null) {
            return Optional.empty();
        }
        Optional<LinkPreview> fetched = linkPreviewFetcher.fetch(url);
        fetched.ifPresentOrElse(preview -> hits.put(url, preview), () -> failures.put(url, Boolean.TRUE));
        return fetched;
    }
}
