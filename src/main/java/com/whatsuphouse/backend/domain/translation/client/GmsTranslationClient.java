package com.whatsuphouse.backend.domain.translation.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.whatsuphouse.backend.global.common.enums.AppLocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * SSAFY GMS(OpenAI 호환 프록시)로 ko→en/ja 번역을 호출하는 클라이언트. (KAN-267)
 *
 * - 키 미설정/만료(키 만료 예정 2026-06-26) 또는 enabled=false면 isEnabled()=false → 자동 번역 비활성(ko fallback).
 * - base-url/model은 application.yml의 기본값을 쓰며 .env(GMS_BASE_URL/GMS_MODEL)로 override 가능.
 */
@Component
public class GmsTranslationClient {

    private static final Logger log = LoggerFactory.getLogger(GmsTranslationClient.class);

    @Value("${gms.base-url}")
    private String baseUrl;

    @Value("${gms.key:}")
    private String apiKey;

    @Value("${gms.model}")
    private String model;

    @Value("${gms.enabled:true}")
    private boolean enabled;

    private final RestClient restClient = RestClient.create();

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /**
     * 한국어 원문을 target 로케일로 번역해 반환한다.
     * 응답이 비어 있으면 null을 반환하고, HTTP 오류 등은 예외로 던진다(상위에서 FAILED 처리).
     */
    public String translate(String koText, AppLocale target) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt(target)),
                        Map.of("role", "user", "content", koText)));

        JsonNode response = restClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            return null;
        }
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        return (content == null || content.isBlank()) ? null : content.trim();
    }

    // 톤 유지 + 브랜드 용어 글로서리를 지시하고, 번역문만 출력하도록 한다.
    private String systemPrompt(AppLocale target) {
        return """
                You are a professional translator for a small social gathering service.
                Translate the user's Korean text into %s.
                Preserve the original tone, nuance, and meaning. Keep it natural and concise.
                Brand glossary: '와썹하우스' = 'Whatsup House', '게더링' = 'gathering'.
                Output ONLY the translated text, with no quotes, labels, or explanation.
                """.formatted(targetLanguageName(target));
    }

    private String targetLanguageName(AppLocale target) {
        return switch (target) {
            case JA -> "Japanese";
            case ZH -> "Simplified Chinese";
            case ES -> "Spanish";
            default -> "English"; // EN (KO는 번역 대상이 아님)
        };
    }
}
