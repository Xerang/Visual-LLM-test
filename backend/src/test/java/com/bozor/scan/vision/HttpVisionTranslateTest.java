package com.bozor.scan.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bozor.common.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Spring → AI 서버 POST /v1/translate 의 실제 HTTP 요청·응답 형식(파이썬 쪽 TranslateRequest/Response와 같아야 한다). */
class HttpVisionTranslateTest {
    final ObjectMapper json = new ObjectMapper();
    HttpServer server;

    HttpVisionClient clientAnswering(int status, String responseBody, AtomicReference<String> received,
                                     AtomicReference<String> contentType) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/translate", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        AppProperties props = new AppProperties("ko", null, null, null,
                new AppProperties.Vision("http", "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(5)), null);
        return new HttpVisionClient(props, json);
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsSnakeCaseUtf8JsonAndReadsTranslations() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> type = new AtomicReference<>();
        HttpVisionClient client = clientAnswering(200, """
                {"translations":[{"locale":"ru","name":"Курага","description":null},
                                 {"locale":"tg","name":"Қайсии хушк","description":"Бе донак"}],
                 "model":"gemini-3.5-flash","prompt_version":"product_translate_v1","elapsed_ms":900}""", body, type);

        VisionTranslateResult result = client.translate(
                new VisionTranslateRequest("ko", List.of("ru", "tg"), "말린 살구", null, "말린 과일"));

        JsonNode sent = json.readTree(body.get());
        assertThat(sent.path("source_locale").asText()).isEqualTo("ko");
        assertThat(sent.path("target_locales").toString()).isEqualTo("[\"ru\",\"tg\"]");
        assertThat(sent.path("name").asText()).isEqualTo("말린 살구");
        assertThat(sent.path("category_name").asText()).isEqualTo("말린 과일");
        assertThat(type.get()).contains("application/json").containsIgnoringCase("utf-8");
        assertThat(result.translations()).extracting(VisionTranslateResult.Translation::name)
                .containsExactly("Курага", "Қайсии хушк");
        assertThat(result.translations().get(0).description()).isNull();
        assertThat(result.promptVersion()).isEqualTo("product_translate_v1");
    }

    @Test
    void rateLimitIsRetryable() throws Exception {
        HttpVisionClient client = clientAnswering(429, "{\"detail\":\"Gemini 요청 한도(429)에 도달했습니다.\"}",
                new AtomicReference<>(), new AtomicReference<>());
        assertThatThrownBy(() -> client.translate(new VisionTranslateRequest("ko", List.of("ru"), "x", null, null)))
                .isInstanceOfSatisfying(VisionException.class, e -> {
                    assertThat(e.retryable()).isTrue();
                    assertThat(e.getMessage()).contains("429");
                });
    }
}
