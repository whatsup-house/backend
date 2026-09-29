package com.whatsuphouse.backend.domain.chat.controller;

import com.whatsuphouse.backend.domain.chat.dto.request.ChatMessageReadRequest;
import com.whatsuphouse.backend.domain.chat.service.ChatService;
import com.whatsuphouse.backend.global.exception.CustomException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/** STOMP 클라이언트 SEND 는 읽음 처리 하나(나머지는 REST). 인증·목적지 검사는 ChatStompInterceptor. */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatSocketController {

    private final ChatService chatService;

    /** SEND /app/rooms/{id}/read {messageId}. 읽음이 전진하면 커밋 후 READ 가 방 구독자에게 브로드캐스트된다. */
    @MessageMapping("/rooms/{id}/read")
    public void markRead(@DestinationVariable UUID id, @Valid @Payload ChatMessageReadRequest request, Principal principal) {
        chatService.markRead(id, UUID.fromString(principal.getName()), request.getMessageId());
    }

    // 다른 방 메시지·비멤버·잘못된 본문: 클라이언트엔 응답하지 않고 무시, 로그만 남긴다.
    @MessageExceptionHandler({CustomException.class, MessagingException.class})
    public void handleReadFailure(Exception e, Principal principal,
                                  @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        Object reason = e instanceof CustomException ce ? ce.getErrorCode() : e.getClass().getSimpleName();
        log.warn("채팅 읽음 처리 무시: destination={}, userId={}, reason={}", destination, principal.getName(), reason);
    }
}
