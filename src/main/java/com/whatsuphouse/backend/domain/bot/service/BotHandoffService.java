package com.whatsuphouse.backend.domain.bot.service;

import com.whatsuphouse.backend.domain.bot.entity.BotDispatch;
import com.whatsuphouse.backend.domain.bot.repository.BotDispatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * 채팅 응답 조립(관리자 화면)이 문의방 이관 사유를 읽는 창구. (KAN-406)
 * 채팅 서비스에 의존하는 BotDispatchService와 분리해 채팅 → 봇 → 채팅 빈 순환을 피한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BotHandoffService {

    private final BotDispatchRepository botDispatchRepository;

    /** 방별 가장 최근 이관 사유. 사유가 없는 방은 빠진다. */
    public Map<UUID, String> findLatestReasons(Collection<UUID> roomIds) {
        BinaryOperator<BotDispatch> latest = BinaryOperator.maxBy(Comparator.comparing(BotDispatch::getUpdatedAt));
        return botDispatchRepository.findAllByConversationIdInAndHandoffReasonIsNotNull(roomIds).stream()
                .collect(Collectors.toMap(BotDispatch::getConversationId, d -> d, latest))
                .values().stream()
                .collect(Collectors.toMap(BotDispatch::getConversationId, BotDispatch::getHandoffReason));
    }
}
