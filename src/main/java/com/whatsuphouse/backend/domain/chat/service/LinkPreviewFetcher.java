package com.whatsuphouse.backend.domain.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 링크 미리보기 OG 조회(docs/chat-design.md 5절). 사용자가 보낸 URL로 서버가 요청하므로 hop마다 SSRF 가드를 통과해야 한다.
 * 실패(차단·타임아웃·비HTML·메타 없음)는 모두 empty + DEBUG 로그. URL은 메시지 본문 일부라 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class LinkPreviewFetcher {

    static final int MAX_REDIRECTS = 5;
    static final int MAX_BODY_BYTES = 512 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_IMAGE_URL_LENGTH = 2048;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; WhatsupHouseLinkPreview/1.0)";

    // RFC 3986 문자만 URL로 본다(뒤에 붙은 한글 조사 등은 제외)
    private static final Pattern URL = Pattern.compile("(?i)https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:!?'\")\\]]+$");
    private static final Pattern META_TAG = Pattern.compile("(?is)<meta\\b[^>]*>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");
    private static final Pattern TITLE_TAG = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern CHARSET = Pattern.compile("(?i)charset=[\"']?([\\w.:-]+)");
    private static final Pattern ENTITY = Pattern.compile("&(#\\d{1,7}|#[xX][0-9a-fA-F]{1,6}|amp|lt|gt|quot|apos|nbsp);");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Cntrl}]+");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final Predicate<String> hostGuard;

    public LinkPreviewFetcher() {
        this(LinkPreviewFetcher::isPublicHost);
    }

    /** 테스트에서 로컬 HttpServer(127.0.0.1)를 쓰기 위해 가드를 바꿔 끼운다. */
    LinkPreviewFetcher(Predicate<String> hostGuard) {
        this.hostGuard = hostGuard;
    }

    public record LinkPreview(String url, String title, String description, String image) {

        /** chat_messages.link_preview JSONB 형태. 값이 없으면 null로 둔다. */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("url", url);
            map.put("title", title);
            map.put("description", description);
            map.put("image", image);
            return map;
        }
    }

    /** 본문의 첫 http/https URL. 끝에 붙은 문장부호는 뺀다. */
    public static Optional<String> extractFirstUrl(String text) {
        Matcher matcher = URL.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(TRAILING_PUNCTUATION.matcher(matcher.group()).replaceFirst(""));
    }

    public Optional<LinkPreview> fetch(String url) {
        try {
            URI uri = new URI(url);
            // hop 0 = 원 요청, 리다이렉트는 최대 MAX_REDIRECTS회까지 따라간다. 매 hop 가드 재검사.
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                if (!isAllowed(uri)) {
                    return fail("차단 대상");
                }
                HttpResponse<byte[]> response = send(uri);
                int status = response.statusCode();
                if (status / 100 == 3) {
                    Optional<String> location = response.headers().firstValue("Location");
                    if (location.isEmpty()) {
                        return fail("Location 없는 리다이렉트");
                    }
                    uri = uri.resolve(location.get());
                    continue;
                }
                if (status / 100 != 2 || !isHtml(response.headers())) {
                    return fail("비HTML 또는 오류 응답 " + status);
                }
                return parse(new String(response.body(), charset(response.headers())), url, uri);
            }
            return fail("리다이렉트 " + MAX_REDIRECTS + "회 초과");
        } catch (URISyntaxException | IllegalArgumentException | ExecutionException | TimeoutException e) {
            return fail(e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("interrupted");
        }
    }

    /** localhost와, DNS 해석 결과 중 하나라도 사설·루프백·링크로컬 등이면 차단. 해석 실패도 차단. */
    // ponytail: 검사 후 HttpClient가 다시 해석하므로 DNS 재바인딩 창이 남는다. 막으려면 해석한 IP로 직접 접속(SNI·Host 처리)해야 한다.
    static boolean isPublicHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".localhost")) {
            return false;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    // 127/8·::1(루프백), 10/8·172.16/12·192.168/16(사설), 169.254/16(링크로컬), 0.0.0.0/8, 멀티캐스트, fc00::/7(ULA)
    static boolean isBlockedAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] raw = address.getAddress();
        return address instanceof Inet6Address ? (raw[0] & 0xfe) == 0xfc : raw[0] == 0;
    }

    private boolean isAllowed(URI uri) {
        String scheme = uri.getScheme();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && uri.getHost() != null && hostGuard.test(uri.getHost());
    }

    private HttpResponse<byte[]> send(URI uri) throws InterruptedException, ExecutionException, TimeoutException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(READ_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html")
                .GET()
                .build();
        // 2xx HTML만 본문을 받는다(MAX_BODY_BYTES에서 잘라 앞부분만 파싱). 리다이렉트·비HTML은 본문을 받지 않고 끊는다.
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, info ->
                new CappedBody(info.statusCode() / 100 == 2 && isHtml(info.headers()) ? MAX_BODY_BYTES : 0));
        try {
            // request.timeout은 헤더 수신까지만 걸리므로 본문 수신까지 포함한 hop 상한(연결 3초 + 읽기 3초)을 둔다.
            return future.get(CONNECT_TIMEOUT.plus(READ_TIMEOUT).toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException e) {
            future.cancel(true);
            throw e;
        }
    }

    private static boolean isHtml(HttpHeaders headers) {
        return headers.firstValue("Content-Type")
                .map(value -> value.trim().toLowerCase(Locale.ROOT).startsWith("text/html"))
                .orElse(false);
    }

    // ponytail: 헤더 charset만 본다(없으면 UTF-8). <meta charset>만 있는 구형 EUC-KR 페이지는 깨지므로 필요해지면 추가.
    private static Charset charset(HttpHeaders headers) {
        Matcher matcher = CHARSET.matcher(headers.firstValue("Content-Type").orElse(""));
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (IllegalArgumentException e) {
                log.debug("[LinkPreview] 알 수 없는 charset, UTF-8로 읽음");
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** og:title·og:description·og:image(title은 없으면 &lt;title&gt;). 셋 다 없으면 empty. */
    static Optional<LinkPreview> parse(String html, String url, URI pageUri) {
        Map<String, String> meta = new HashMap<>();
        Matcher tag = META_TAG.matcher(html);
        while (tag.find()) {
            Map<String, String> attributes = attributes(tag.group());
            String key = attributes.getOrDefault("property", attributes.get("name"));
            String content = attributes.get("content");
            if (key != null && content != null) {
                meta.putIfAbsent(key.toLowerCase(Locale.ROOT), content);
            }
        }
        String title = clean(meta.get("og:title"), MAX_TITLE_LENGTH);
        if (title == null) {
            Matcher titleTag = TITLE_TAG.matcher(html);
            title = titleTag.find() ? clean(titleTag.group(1), MAX_TITLE_LENGTH) : null;
        }
        String description = clean(meta.get("og:description"), MAX_DESCRIPTION_LENGTH);
        String image = toHttpUrl(pageUri, clean(meta.get("og:image"), MAX_IMAGE_URL_LENGTH));
        if (title == null && description == null && image == null) {
            return fail("OG 메타 없음");
        }
        return Optional.of(new LinkPreview(url, title, description, image));
    }

    private static Map<String, String> attributes(String tag) {
        Map<String, String> attributes = new HashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(tag);
        while (matcher.find()) {
            String value = matcher.group(2);
            if (value == null) {
                value = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            }
            attributes.putIfAbsent(matcher.group(1).toLowerCase(Locale.ROOT), value);
        }
        return attributes;
    }

    private static String clean(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String text = WHITESPACE.matcher(decodeEntities(raw)).replaceAll(" ").trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        // 서로게이트 쌍을 반으로 자르지 않는다
        return text.substring(0, Character.isHighSurrogate(text.charAt(maxLength - 1)) ? maxLength - 1 : maxLength);
    }

    private static String decodeEntities(String text) {
        return ENTITY.matcher(text).replaceAll(match -> Matcher.quoteReplacement(decodeEntity(match.group(1))));
    }

    private static String decodeEntity(String name) {
        return switch (name) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "nbsp" -> " ";
            default -> {
                boolean isHex = name.charAt(1) == 'x' || name.charAt(1) == 'X';
                int codePoint = isHex ? Integer.parseInt(name.substring(2), 16) : Integer.parseInt(name.substring(1));
                yield Character.isValidCodePoint(codePoint) && Character.getType(codePoint) != Character.SURROGATE
                        ? Character.toString(codePoint) : "";
            }
        };
    }

    // 상대 경로는 최종 페이지 기준으로 풀고, http/https가 아니면(javascript:, data: 등) 버린다.
    private static String toHttpUrl(URI base, String reference) {
        if (reference == null) {
            return null;
        }
        try {
            URI resolved = base.resolve(reference.replace(" ", "%20"));
            String scheme = resolved.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) ? resolved.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Optional<LinkPreview> fail(String reason) {
        log.debug("[LinkPreview] 미리보기 없음: {}", reason);
        return Optional.empty();
    }

    /** 최대 limit 바이트까지만 받고 구독을 끊는다. limit 0이면 본문을 받지 않는다. */
    private static final class CappedBody implements HttpResponse.BodySubscriber<byte[]> {

        private final int limit;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        CappedBody(int limit) {
            this.limit = limit;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (limit == 0) {
                subscription.cancel();
                body.complete(new byte[0]);
                return;
            }
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer item : items) {
                int length = Math.min(item.remaining(), limit - buffer.size());
                byte[] chunk = new byte[length];
                item.get(chunk);
                buffer.write(chunk, 0, length);
            }
            if (buffer.size() >= limit) {
                subscription.cancel();
                body.complete(buffer.toByteArray());
            }
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(buffer.toByteArray());
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }
    }
}
