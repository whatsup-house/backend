package com.whatsuphouse.backend.domain.chat.socket;

import com.whatsuphouse.backend.domain.chat.service.ChatSocketService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 지금 /topic/rooms/{roomId}를 구독 중인 (세션, 구독, 사용자). 웹 푸시 일감이 "소켓 미구독자에게만 발송" 판단에 쓴다.
 * SessionSubscribeEvent는 인바운드 인터셉터(멤버십 검사)를 통과한 구독에만 발행된다.
 * ponytail: 단일 인스턴스 인메모리, 방별 조회는 전체 구독 스캔. 스케일아웃·동접 수천이면 Redis로 옮기고 방별 인덱스.
 */
@Component
public class ChatPresenceRegistry {

    public record RoomSubscription(String sessionId, String subscriptionId, UUID roomId, UUID userId) {
    }

    // sessionId → (subscriptionId → 구독)
    private final Map<String, Map<String, RoomSubscription>> sessions = new ConcurrentHashMap<>();

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        if (event.getUser() == null) {
            return;
        }
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        String subscriptionId = headers.getSubscriptionId();
        UUID userId = UUID.fromString(event.getUser().getName());
        ChatSocketService.parseRoomId(headers.getDestination()).ifPresent(roomId ->
                sessions.computeIfAbsent(sessionId, id -> new ConcurrentHashMap<>())
                        .put(subscriptionId, new RoomSubscription(sessionId, subscriptionId, roomId, userId)));
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Map<String, RoomSubscription> subscriptions = sessions.get(headers.getSessionId());
        if (subscriptions != null) {
            subscriptions.remove(headers.getSubscriptionId());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
    }

    public Set<UUID> listSubscriberIds(UUID roomId) {
        return sessions.values().stream()
                .flatMap(subscriptions -> subscriptions.values().stream())
                .filter(s -> s.roomId().equals(roomId))
                .map(RoomSubscription::userId)
                .collect(Collectors.toSet());
    }

    /** 멤버가 아니게 된 사용자의 이 방 구독을 목록에서 떼어 돌려준다(브로커 쪽 구독 해제는 호출 측). */
    public List<RoomSubscription> revoke(UUID roomId, Collection<UUID> userIds) {
        List<RoomSubscription> revoked = new ArrayList<>();
        if (userIds.isEmpty()) {
            return revoked;
        }
        sessions.values().forEach(subscriptions -> subscriptions.values().removeIf(s -> {
            boolean match = s.roomId().equals(roomId) && userIds.contains(s.userId());
            if (match) {
                revoked.add(s);
            }
            return match;
        }));
        return revoked;
    }
}
