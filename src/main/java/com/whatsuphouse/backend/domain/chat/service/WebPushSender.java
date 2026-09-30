package com.whatsuphouse.backend.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsuphouse.backend.domain.chat.entity.PushSubscription;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 웹 푸시 발송(VAPID, RFC 8291 aes128gcm). VAPID 키·subject 중 하나라도 비어 있으면 비활성(isEnabled=false).
 * 로그에 endpoint·키·페이로드를 남기지 않는다(구독 ID와 상태 코드만).
 * ponytail: 라이브러리가 발송마다 HTTP 클라이언트를 새로 만든다. 발송량이 커지면 PushAsyncService(클라이언트 재사용)로 교체.
 */
@Slf4j
@Component
public class WebPushSender {

    // 하루 지난 채팅 알림은 의미가 없어 푸시 서비스가 버리게 한다.
    private static final int TTL_SECONDS = 24 * 60 * 60;
    // 응답 없는 endpoint가 발송 스레드를 붙잡지 않게 한다.
    private static final long TIMEOUT_SECONDS = 10;

    private final String publicKey;
    private final PushService pushService;
    private final ObjectMapper objectMapper;

    public WebPushSender(@Value("${chat.push.vapid-public-key:}") String publicKey,
                         @Value("${chat.push.vapid-private-key:}") String privateKey,
                         @Value("${chat.push.vapid-subject:}") String subject,
                         ObjectMapper objectMapper) {
        this.publicKey = publicKey.trim();
        this.objectMapper = objectMapper;
        if (this.publicKey.isEmpty() || privateKey.isBlank() || subject.isBlank()) {
            log.info("[WebPush] VAPID 미설정 — 웹 푸시 비활성");
            this.pushService = null;
            return;
        }
        // web-push가 키 로딩에 "BC" 프로바이더를 이름으로 찾는다.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            this.pushService = new PushService(this.publicKey, privateKey.trim(), subject.trim());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID 키가 올바르지 않습니다.");
        }
    }

    public boolean isEnabled() {
        return pushService != null;
    }

    public String getPublicKey() {
        return publicKey;
    }

    /**
     * 구독 하나에 발송한다. 활성(isEnabled) 상태에서만 호출한다.
     * @return 푸시 서비스가 404/410으로 구독 만료를 알렸으면 true(호출 측이 구독을 지운다). 그 외 실패는 WARN 로그만 남기고 false.
     */
    public boolean send(PushSubscription subscription, Object payload) {
        Future<HttpResponse> future = null;
        try {
            Notification notification = new Notification(subscription.getEndpoint(), subscription.getP256dh(),
                    subscription.getAuth(), objectMapper.writeValueAsBytes(payload), TTL_SECONDS);
            future = pushService.sendAsync(notification, Encoding.AES128GCM);
            int status = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                return true;
            }
            if (status >= 300) {
                log.warn("[WebPush] 발송 실패 subscriptionId={} status={}", subscription.getId(), status);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[WebPush] 발송 중단 subscriptionId={}", subscription.getId());
        } catch (Exception e) {
            // 잘못된 구독 키, 타임아웃, 연결 실패 등
            log.warn("[WebPush] 발송 실패 subscriptionId={} cause={}", subscription.getId(), e.getClass().getSimpleName());
        } finally {
            // 끝난 요청엔 영향 없음. 타임아웃·중단이면 취소 콜백이 라이브러리의 HTTP 클라이언트를 닫는다.
            if (future != null) {
                future.cancel(true);
            }
        }
        return false;
    }
}
