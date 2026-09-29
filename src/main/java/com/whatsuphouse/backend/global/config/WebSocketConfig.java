package com.whatsuphouse.backend.global.config;

import com.whatsuphouse.backend.domain.chat.socket.ChatStompInterceptor;
import com.whatsuphouse.backend.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * 채팅 실시간(STOMP over WebSocket, SockJS 없음). docs/chat-design.md 3절.
 * 송신은 REST, 소켓은 수신 + 읽음 처리(/app/rooms/{id}/read)만.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatStompInterceptor chatStompInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOrigins(SecurityConfig.ALLOWED_ORIGINS.toArray(String[]::new));
        // 거부 사유를 ERROR 프레임 message 헤더에 ErrorCode 이름으로 싣는다(FE가 TOKEN_EXPIRED면 토큰 갱신 후 재연결 등).
        // STOMP 규약상 ERROR 뒤에는 서버가 연결을 닫는다(Spring 고정 동작).
        registry.setErrorHandler(new StompSubProtocolErrorHandler() {
            @Override
            protected Message<byte[]> handleInternal(StompHeaderAccessor errorHeaderAccessor, byte[] errorPayload,
                                                     Throwable cause, StompHeaderAccessor clientHeaderAccessor) {
                if (cause != null && NestedExceptionUtils.getMostSpecificCause(cause) instanceof CustomException e) {
                    errorHeaderAccessor.setMessage(e.getErrorCode().name());
                }
                return super.handleInternal(errorHeaderAccessor, errorPayload, cause, clientHeaderAccessor);
            }
        });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // ponytail: 인메모리 SimpleBroker, 단일 인스턴스 전제. 스케일아웃 시 Redis pub/sub 브리지(또는 외부 STOMP 브로커 릴레이)로 교체.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatStompInterceptor);
    }
}
