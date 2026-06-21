package com.whatsuphouse.backend.domain.translation.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.whatsuphouse.backend.global.common.enums.AppLocale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * GMS(SSAFY) 경유 OpenAI 호환 번역 클라이언트. (KAN-267)
 * 키는 서버 환경변수(GMS_KEY)로만 주입한다. 키가 없으면 비활성(isEnabled=false).
 */
@Slf4j
@Component
public class GmsTranslationClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final boolean enabled;
    private final RestClient restClient = RestClient.create();

    public GmsTranslationClient(
            @Value("${gms.base-url}") String baseUrl,
            @Value("${gms.key:}") String apiKey,
            @Value("${gms.model}") String model,
            @Value("${gms.enabled:true}") boolean enabled) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled && StringUtils.hasText(apiKey);
    }

    /**
     * 한국어 텍스트를 target 언어로 번역한다. 결과가 비면 null을 반환하고,
     * HTTP 오류 등은 호출측에서 처리하도록 예외를 전파한다(상태 FAILED 기록).
     */
    public String translate(String koText, AppLocale target) {
        String targetName = switch (target) {
            case EN -> "English";
            case JA -> "Japanese";
            case KO -> "Korean";
        };
        String system = "You are a professional translator for a Korean social gathering app. "
                + "Translate the user's Korean text into " + targetName + ". "
                + "Output ONLY the translation with no quotes or explanation. "
                + "Preserve tone and line breaks. Keep brand terms (와썹하우스, 게더링) natural.";

        JsonNode response = restClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", model,
                        "temperature", 0.3,
                        "messages", List.of(
                                Map.of("role", "system", "content", system),
                                Map.of("role", "user", "content", koText)
                        )
                ))
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            return null;
        }
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        return content == null ? null : content.trim();
    }
}
