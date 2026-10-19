package com.whatsuphouse.backend.domain.bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsuphouse.backend.domain.application.client.service.DiningApplicationService;
import com.whatsuphouse.backend.domain.bot.entity.BotDispatch;
import com.whatsuphouse.backend.domain.chat.service.ChatService;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 그록봇 웹훅 본문. 고객 데이터는 dispatch.customerId 한 명의 "내 것" 조회 서비스로만 모은다(다른 고객 조회 메서드 금지).
 * 이메일은 빼고 전화번호는 뒤 4자리, 계좌번호는 앞 3자리+***로 가린다. (KAN-406)
 * 본문: {eventId, conversationId, messageId, customer{id, displayName}, message{text, createdAt}, history[],
 *        context{applications[], tickets{}, depositRequired}, faq[{q, a}], replyUrl}
 */
@Component
@RequiredArgsConstructor
public class BotPayloadBuilder {

    static final int HISTORY_SIZE = 10;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ChatService chatService;
    private final UserService userService;
    private final DiningApplicationService diningApplicationService;
    private final TicketService ticketService;
    private final ObjectMapper objectMapper;
    private final List<Map<String, String>> faq = parseFaq(readFaq());

    @Value("${bot.reply-url:}")
    private String replyUrl;

    // 조회 서비스가 던지는 예외가 발송 트랜잭션(행 잠금)을 rollback-only로 만들지 않도록 따로 돈다.
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public String build(BotDispatch dispatch) {
        UUID customerId = dispatch.getCustomerId();
        User customer = userService.findUser(customerId);

        Map<String, Object> customerNode = new LinkedHashMap<>();
        customerNode.put("id", customerId);
        customerNode.put("displayName", customer.getNickname());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("applications", diningApplicationService.listMyApplications(customerId).getApplications());
        context.put("tickets", ticketService.getMyTickets(customerId));
        context.put("depositRequired", customer.isDepositRequired());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", dispatch.getEventId());
        body.put("conversationId", dispatch.getConversationId());
        body.put("messageId", dispatch.getMessageId());
        body.put("customer", customerNode);
        body.put("message", toNode(chatService.getBotTranscriptMessage(dispatch.getMessageId()), false));
        body.put("history", chatService.listBotTranscript(dispatch.getConversationId(), HISTORY_SIZE).stream()
                .map(m -> toNode(m, true)).toList());
        body.put("context", mask(objectMapper.convertValue(context, Map.class)));
        body.put("faq", faq);
        body.put("replyUrl", replyUrl);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Object> toNode(ChatService.BotTranscriptMessage message, boolean withRole) {
        Map<String, Object> node = new LinkedHashMap<>();
        if (withRole) {
            node.put("role", message.role());
        }
        node.put("text", message.text());
        node.put("createdAt", iso(message.createdAt()));
        return node;
    }

    private static String iso(LocalDateTime at) {
        return at.atZone(SEOUL).toOffsetDateTime().toString();
    }

    /** 키 이름 기준 개인정보 가림: *email* 제거, *phone* 뒤 4자리, *account*number* 앞 3자리+***. */
    static Object mask(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                String key = String.valueOf(k);
                String lower = key.toLowerCase(Locale.ROOT);
                if (lower.contains("email")) {
                    return;
                }
                if (lower.contains("phone") && v instanceof String s) {
                    String digits = s.replaceAll("\\D", "");
                    masked.put(key, digits.length() <= 4 ? digits : digits.substring(digits.length() - 4));
                } else if (lower.contains("account") && lower.contains("number") && v instanceof String s) {
                    masked.put(key, s.substring(0, Math.min(3, s.length())) + "***");
                } else {
                    masked.put(key, mask(v));
                }
            });
            return masked;
        }
        if (node instanceof List<?> list) {
            return list.stream().map(BotPayloadBuilder::mask).toList();
        }
        return node;
    }

    private static String readFaq() {
        try {
            return new ClassPathResource("cs/faq.md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "## 질문" 줄부터 다음 "## " 전까지를 답으로 묶는다. 그 밖의 줄(# 제목 등)은 무시. */
    static List<Map<String, String>> parseFaq(String markdown) {
        List<Map<String, String>> items = new ArrayList<>();
        String question = null;
        StringBuilder answer = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ")) {
                addFaq(items, question, answer);
                question = line.substring(3).trim();
                answer.setLength(0);
            } else if (question != null && !line.isBlank()) {
                answer.append(answer.isEmpty() ? "" : "\n").append(line.trim());
            }
        }
        addFaq(items, question, answer);
        return List.copyOf(items);
    }

    private static void addFaq(List<Map<String, String>> items, String question, StringBuilder answer) {
        if (question != null && !answer.isEmpty()) {
            items.add(Map.of("q", question, "a", answer.toString()));
        }
    }
}
