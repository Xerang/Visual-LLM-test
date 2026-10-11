package com.bozor.scan.vision;

import com.bozor.common.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** FastAPI POST /v1/scan·/v1/translate 호출. HTTP 상태를 VisionException 코드로 바꾼다(재시도 여부 포함). */
@Component
@ConditionalOnProperty(prefix = "app.vision", name = "mode", havingValue = "http", matchIfMissing = true)
public class HttpVisionClient implements VisionClient {
    private static final Logger LOG = LoggerFactory.getLogger(HttpVisionClient.class);
    private static final MediaType JSON_UTF8 = MediaType.parseMediaType("application/json; charset=UTF-8");

    /** 번역은 판매자가 화면에서 기다리므로 스캔(워커)보다 짧게 기다린다. 글자만 보내서 보통 몇 초면 끝난다. */
    static final Duration TRANSLATE_READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient client;
    private final RestClient translateClient;
    private final ObjectMapper json;

    public HttpVisionClient(AppProperties properties, ObjectMapper json) {
        this.client = restClient(properties.vision().url(), properties.vision().readTimeout());
        this.translateClient = restClient(properties.vision().url(), TRANSLATE_READ_TIMEOUT);
        this.json = json;
    }

    private static RestClient restClient(String baseUrl, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public VisionScanResult scan(VisionScanRequest request) {
        return parse(call(() -> client.post().uri("/v1/scan")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart(request))
                .retrieve()
                .body(String.class)));
    }

    @Override
    public VisionTranslateResult translate(VisionTranslateRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source_locale", request.sourceLocale());
        payload.put("target_locales", request.targetLocales());
        payload.put("name", request.name());
        payload.put("description", request.description());
        payload.put("category_name", request.categoryName());
        String requestBody;
        try {
            requestBody = json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        String body = call(() -> translateClient.post().uri("/v1/translate")
                .contentType(JSON_UTF8)   // 한글·키릴 문자가 깨지지 않게 문자셋을 명시
                .body(requestBody)
                .retrieve()
                .body(String.class));
        try {
            JsonNode root = json.readTree(body);
            List<VisionTranslateResult.Translation> list = new ArrayList<>();
            for (JsonNode t : root.path("translations")) {
                list.add(new VisionTranslateResult.Translation(t.path("locale").asText(""), t.path("name").asText(""),
                        t.path("description").isNull() ? null : t.path("description").asText(null)));
            }
            return new VisionTranslateResult(root.path("model").asText("unknown"),
                    root.path("prompt_version").asText("unknown"), list);
        } catch (JsonProcessingException e) {
            throw new VisionException(VisionException.BAD_RESPONSE, false, "AI 응답을 읽지 못했습니다.");
        }
    }

    /** 호출 실패를 VisionException 으로 바꾼다(스캔·번역 공통). */
    private String call(Supplier<String> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw fromStatus(e.getStatusCode().value(), detailOf(e));
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new VisionException(VisionException.TIMEOUT, true, "AI 응답이 너무 오래 걸립니다.");
            }
            throw new VisionException(VisionException.UNAVAILABLE, true, "AI 서버에 연결하지 못했습니다.");
        } catch (RestClientException e) {
            LOG.warn("Vision call failed", e);
            throw new VisionException(VisionException.UNAVAILABLE, true, "AI 서버 호출에 실패했습니다.");
        }
    }

    private MultiValueMap<String, Object> multipart(VisionScanRequest request) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (int i = 0; i < request.images().size(); i++) {
            String filename = "image-" + i + ".jpg";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            parts.add("images", new HttpEntity<>(new ByteArrayResource(request.images().get(i)) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }, headers));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("output_locale", request.outputLocale());
        payload.put("caption_text", request.captionText());
        payload.put("categories", request.categories().stream()
                .map(c -> Map.of("code", c.code(), "name", c.name())).toList());
        payload.put("max_candidates", request.maxCandidates());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JSON_UTF8);   // 카테고리 이름(한글·키릴)이 깨지지 않게 문자셋을 명시
        try {
            parts.add("request", new HttpEntity<>(json.writeValueAsString(payload), headers));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return parts;
    }

    private VisionScanResult parse(String body) {
        try {
            JsonNode root = json.readTree(body);
            if (root == null || !root.path("candidates").isArray()) {
                throw new VisionException(VisionException.BAD_RESPONSE, false, "AI 응답 형식이 올바르지 않습니다.");
            }
            List<VisionScanResult.Candidate> candidates = new ArrayList<>();
            for (JsonNode c : root.path("candidates")) {
                JsonNode price = c.path("suggested_price");
                candidates.add(new VisionScanResult.Candidate(
                        c.path("name").asText(""),
                        c.path("description").isNull() ? null : c.path("description").asText(null),
                        c.path("category_code").asText(""),
                        strings(c.path("visible_text")),
                        strings(c.path("review_notes")),
                        price.isNumber() ? price.decimalValue() : null));
            }
            return new VisionScanResult(root, root.path("model").asText("unknown"),
                    root.path("prompt_version").asText("unknown"), root.path("elapsed_ms").asInt(0),
                    candidates, strings(root.path("image_notes")));
        } catch (JsonProcessingException e) {
            throw new VisionException(VisionException.BAD_RESPONSE, false, "AI 응답을 읽지 못했습니다.");
        }
    }

    private static List<String> strings(JsonNode array) {
        List<String> result = new ArrayList<>();
        if (array.isArray()) array.forEach(n -> result.add(n.asText()));
        return result;
    }

    private String detailOf(RestClientResponseException e) {
        try {
            String detail = json.readTree(e.getResponseBodyAsString()).path("detail").asText("");
            return detail.isBlank() ? null : detail;
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    /** docs/03-api.md 5.5: 429·504·503은 재시도, 400·413·502는 바로 실패. */
    static VisionException fromStatus(int status, String detail) {
        return switch (status) {
            case 429 -> new VisionException(VisionException.RATE_LIMITED, true,
                    or(detail, "AI 요청 한도에 도달했습니다. 잠시 후 다시 시도합니다."));
            case 504 -> new VisionException(VisionException.TIMEOUT, true, or(detail, "AI 응답이 너무 오래 걸립니다."));
            case 503 -> new VisionException(VisionException.UNAVAILABLE, true, or(detail, "AI 서버를 사용할 수 없습니다."));
            case 400, 413 -> new VisionException(VisionException.IMAGE_INVALID, false, or(detail, "AI 서버가 사진을 읽지 못했습니다."));
            default -> new VisionException(VisionException.BAD_RESPONSE, false, or(detail, "AI 분석에 실패했습니다."));
        };
    }

    private static String or(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
