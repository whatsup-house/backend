package com.whatsuphouse.backend.domain.chat.socket;

import com.whatsuphouse.backend.domain.chat.service.ChatService;
import com.whatsuphouse.backend.domain.chat.service.ChatSocketService;
import com.whatsuphouse.backend.global.auth.JwtTokenProvider;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/**
 * STOMP 인바운드 검사. 거부는 CustomException → STOMP ERROR 프레임(message 헤더 = ErrorCode 이름, WebSocketConfig).
 * - CONNECT: Authorization: Bearer JWT 네이티브 헤더만 본다(URL 쿼리·쿠키 토큰 미사용). 정지·탈퇴 계정 거부.
 * - SUBSCRIBE: /topic/rooms/{roomId}는 참여 중 멤버만, /user/queue/rooms(본인 큐)만 허용. 그 외 목적지·와일드카드 거부.
 * - SEND: /app/** 만. 클라이언트가 /topic·/user 로 직접 보내 이벤트를 위조하는 것을 막는다.
 */
@Component
@RequiredArgsConstructor
public class ChatStompInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ROOMS_QUEUE = "/user" + ChatSocketService.ROOMS_QUEUE;
    private static final String APP_PREFIX = "/app/";

    private final JwtTokenProvider jwtTokenProvider;
    private final ChatService chatService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
            case SUBSCRIBE -> checkSubscribe(accessor.getDestination(), accessor.getUser());
            case SEND -> checkSend(accessor.getDestination());
            default -> {
                // UNSUBSCRIBE, DISCONNECT, ACK 등은 통과
            }
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        jwtTokenProvider.validateToken(token);
        UserPrincipal principal = jwtTokenProvider.getUserPrincipal(token);
        chatService.checkConnectable(principal.getUserId());
        // getName() = userId 문자열 → /user/{userId}/queue/rooms 라우팅 키
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void checkSubscribe(String destination, Principal user) {
        if (user == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        if (USER_ROOMS_QUEUE.equals(destination)) {
            return;
        }
        Optional<UUID> roomId = ChatSocketService.parseRoomId(destination);
        if (roomId.isEmpty()) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        chatService.checkSubscribe(roomId.get(), UUID.fromString(user.getName()));
    }

    private void checkSend(String destination) {
        if (destination == null || !destination.startsWith(APP_PREFIX)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
