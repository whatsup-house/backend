package com.whatsuphouse.backend.domain.chat.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.whatsuphouse.backend.domain.chat.service.LinkPreviewFetcher.LinkPreview;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** SSRF 가드·리다이렉트 상한·OG 파싱. 외부 네트워크 없이 로컬 HttpServer(127.0.0.1)로 검증한다. */
class LinkPreviewFetcherTest {

    private static final String OG_HTML = """
            <html><head>
            <meta content="와썹 &amp; 하우스" property="og:title">
            <meta property='og:description' content='  소규모
               게더링  '>
            <meta property="og:image" content="/img/cover.png?a=1&amp;b=2">
            <title>무시되는 제목</title>
            </head><body>본문</body></html>
            """;

    // 로컬 서버용으로 127.0.0.1만 통과시킨다. localhost 등 다른 호스트는 막는다.
    private final LinkPreviewFetcher fetcher = new LinkPreviewFetcher("127.0.0.1"::equals);
    private final AtomicInteger hits = new AtomicInteger();
    private HttpServer server;
    private String base;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
        port = server.getAddress().getPort();
        base = "http://127.0.0.1:" + port;
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("사설 IP·루프백·링크로컬·ULA·localhost는 차단하고, 실제 가드로는 로컬 서버에 요청조차 보내지 않는다")
    void blocksPrivateAndLocalHosts() {
        for (String host : List.of("localhost", "api.localhost", "10.0.0.1", "172.16.0.1", "172.31.255.255",
                "192.168.0.1", "127.0.0.1", "127.8.8.8", "169.254.169.254", "0.0.0.0", "[::1]", "[fc00::1]", "[fd12:3456::1]")) {
            assertThat(LinkPreviewFetcher.isPublicHost(host)).as(host).isFalse();
        }
        assertThat(LinkPreviewFetcher.isPublicHost("8.8.8.8")).isTrue();
        assertThat(LinkPreviewFetcher.isPublicHost("172.32.0.1")).isTrue();
        assertThat(LinkPreviewFetcher.isPublicHost("[2001:4860:4860::8888]")).isTrue();

        LinkPreviewFetcher realGuard = new LinkPreviewFetcher();
        assertThat(realGuard.fetch(base + "/og")).isEmpty();
        assertThat(realGuard.fetch("http://localhost:" + port + "/og")).isEmpty();
        assertThat(hits).hasValue(0);
    }

    @Test
    @DisplayName("리다이렉트는 5회까지만 따라가고, hop마다 가드를 다시 검사한다")
    void limitsRedirectsAndRechecksEachHop() {
        assertThat(fetcher.fetch(base + "/redirect/5")).isPresent();

        hits.set(0);
        assertThat(fetcher.fetch(base + "/redirect/6")).isEmpty();
        assertThat(hits).hasValue(6); // 원 요청 + 리다이렉트 5회, 6번째 Location은 따라가지 않는다

        hits.set(0);
        assertThat(fetcher.fetch(base + "/to-localhost")).isEmpty();
        assertThat(hits).hasValue(1); // localhost로의 리다이렉트는 요청 전에 차단
    }

    @Test
    @DisplayName("og:title·og:description·og:image를 뽑고, og:title이 없으면 <title>, HTML이 아니면 empty")
    void parsesOgTags() {
        Optional<LinkPreview> og = fetcher.fetch(base + "/og");
        assertThat(og).contains(new LinkPreview(base + "/og", "와썹 & 하우스", "소규모 게더링", base + "/img/cover.png?a=1&b=2"));

        assertThat(fetcher.fetch(base + "/title"))
                .contains(new LinkPreview(base + "/title", "제목만 & 있음", null, null));

        assertThat(fetcher.fetch(base + "/json")).isEmpty();
    }

    @Test
    @DisplayName("본문의 첫 http/https URL만, 뒤에 붙은 문장부호·한글 없이 뽑는다")
    void extractsFirstUrl() {
        assertThat(LinkPreviewFetcher.extractFirstUrl("여기 어때요 https://example.com/menu?id=1, 아니면 http://b.com"))
                .contains("https://example.com/menu?id=1");
        assertThat(LinkPreviewFetcher.extractFirstUrl("(https://example.com/a)에서 봐요")).contains("https://example.com/a");
        assertThat(LinkPreviewFetcher.extractFirstUrl("ftp://example.com 링크 없음")).isEmpty();
    }

    private void handle(HttpExchange exchange) throws IOException {
        hits.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/redirect/")) {
            int left = Integer.parseInt(path.substring("/redirect/".length()));
            if (left == 0) {
                respond(exchange, "text/html; charset=utf-8", OG_HTML);
            } else {
                redirect(exchange, "/redirect/" + (left - 1));
            }
            return;
        }
        switch (path) {
            case "/og" -> respond(exchange, "text/html; charset=utf-8", OG_HTML);
            case "/title" -> respond(exchange, "text/html", "<html><head><title> 제목만 &amp; 있음 </title></head></html>");
            case "/to-localhost" -> redirect(exchange, "http://localhost:" + port + "/og");
            default -> respond(exchange, "application/json", "{\"title\":\"x\"}");
        }
    }

    private static void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        } catch (IOException e) {
            // 클라이언트가 비HTML 본문을 받지 않고 끊는 경우
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}
