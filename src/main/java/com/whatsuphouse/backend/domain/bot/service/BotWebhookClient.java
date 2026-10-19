package com.whatsuphouse.backend.domain.bot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 그록봇 웹훅 발송. POST {bot.webhook-url}, Authorization: Bearer {bot.webhook-key}, 연결 5초·응답 10초.
 * 2xx가 아니면(3xx 포함) RestClientResponseException, 연결 실패·타임아웃은 ResourceAccessException을 던진다(재시도는 호출 측).
 * 키·본문은 로그에 남기지 않는다. (KAN-406)
 */
@Component
public class BotWebhookClient {

    @Value("${bot.webhook-url:}")
    private String webhookUrl;

    @Value("${bot.webhook-key:}")
    private String webhookKey;

    // 테스트가 MockRestServiceServer에 묶은 클라이언트로 바꿔 끼운다.
    private RestClient restClient = RestClient.builder().requestFactory(requestFactory()).build();

    public boolean isConfigured() {
        return StringUtils.hasText(webhookUrl) && StringUtils.hasText(webhookKey);
    }

    public void send(String jsonBody) {
        // 기본 처리기는 4xx·5xx만 예외라, POST에서 따라가지 않는 3xx도 실패로 돌린다.
        HttpStatusCode status = restClient.post()
                .uri(webhookUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + webhookKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
        if (!status.is2xxSuccessful()) {
            throw new RestClientResponseException("bot webhook non-2xx", status, "", null, null, null);
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return factory;
    }
}
