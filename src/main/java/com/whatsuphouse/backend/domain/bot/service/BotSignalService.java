package com.whatsuphouse.backend.domain.bot.service;

import com.whatsuphouse.backend.domain.bot.enums.BotDispatchStatus;
import com.whatsuphouse.backend.domain.bot.repository.BotDispatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 그록봇에게 "새 문의 있음"을 Slack Incoming Webhook(bot.slack-signal-webhook-url, #whatsup-bot)으로 알린다. (KAN-406)
 * 본문은 고정 문구뿐이다: 고객 정보·메시지·ID 일체 없음. 에이전트는 신호를 받고 GET /api/bot/pending으로 가져간다.
 * - 마지막 성공 신호 이후 조회 가능해진(send_after 도래) PENDING이 있을 때만, 최대 30초에 1회.
 * - 실패하면 최대 3회 재시도, 그래도 실패면 PENDING은 그대로 두고 30초 뒤 다시 신호한다.
 * 주소 미설정이면 신호만 꺼진다(대기 건은 그대로 pull 가능). 주소·응답 본문은 로그에 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotSignalService {

    static final String BODY = "{\"text\":\"new-inquiry\"}";
    static final LocalDateTime NEVER = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final Duration INTERVAL = Duration.ofSeconds(30);

    private final BotDispatchRepository botDispatchRepository;

    @Value("${bot.slack-signal-webhook-url:}")
    private String webhookUrl;

    private final RestClient restClient = RestClient.builder().requestFactory(requestFactory()).build();

    // 재시도 간격(최대 3회). 테스트는 0으로 바꿔 끼운다.
    private List<Duration> retryBackoff = List.of(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(10));

    // 서버 1대 운영이라 메모리에 둔다. 재시작하면 남은 대기 건에 신호가 한 번 더 갈 뿐이다.
    // signaledUpTo: 마지막 성공 신호 시각(이때까지 조회 가능해진 건은 알렸다). nextSignalAt: 마지막 시도 + 30초.
    private LocalDateTime signaledUpTo = NEVER;
    private LocalDateTime nextSignalAt = NEVER;

    public boolean isConfigured() {
        return StringUtils.hasText(webhookUrl);
    }

    /** 스케줄러가 2초마다 부른다(봇 활성일 때만). */
    public synchronized void signalIfDue(LocalDateTime now) {
        if (!isConfigured() || now.isBefore(nextSignalAt)
                || !botDispatchRepository.existsByStatusAndSendAfterGreaterThanAndSendAfterLessThanEqual(
                        BotDispatchStatus.PENDING, signaledUpTo, now)) {
            return;
        }
        nextSignalAt = now.plus(INTERVAL);
        if (send()) {
            signaledUpTo = now;
        }
    }

    private boolean send() {
        for (int attempt = 0; ; attempt++) {
            String error;
            try {
                HttpStatusCode status = restClient.post()
                        .uri(webhookUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BODY)
                        .retrieve()
                        .toBodilessEntity()
                        .getStatusCode();
                if (status.is2xxSuccessful()) {
                    return true;
                }
                error = "HTTP " + status.value();
            } catch (RestClientResponseException e) {
                error = "HTTP " + e.getStatusCode().value();
            } catch (RuntimeException e) {
                error = e.getClass().getSimpleName();
            }
            if (attempt >= retryBackoff.size()) {
                log.warn("그록봇 Slack 신호 실패: attempts={}, error={}", attempt + 1, error);
                return false;
            }
            try {
                Thread.sleep(retryBackoff.get(attempt).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return factory;
    }
}
