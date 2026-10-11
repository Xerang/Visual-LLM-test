package com.bozor.poc;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class AnalysisController {
    private static final Logger LOG = LoggerFactory.getLogger(AnalysisController.class);
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RestClient visionClient;
    private final ScanRecordRepository records;

    public AnalysisController(@Value("${vision.service-url}") String serviceUrl,
                              @Value("${vision.read-timeout-seconds:300}") long readTimeoutSeconds,
                              ScanRecordRepository records) {
        this.records = records;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.visionClient = RestClient.builder().baseUrl(serviceUrl).requestFactory(requestFactory).build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("spring-ok");
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyze(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("사진을 선택해 주세요."));
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_TYPES.contains(mime.toLowerCase())) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(new ErrorResponse("JPEG, PNG, WEBP 사진만 업로드할 수 있습니다."));
        }
        if (file.getSize() > MAX_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new ErrorResponse("사진은 5MB 이하로 올려주세요."));
        }

        byte[] bytes = file.getBytes();
        ByteArrayResource image = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "product-image"; }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(mime));
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new HttpEntity<>(image, fileHeaders));

        try {
            String responseBody = visionClient.post()
                    .uri("/analyze")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                LOG.error("FastAPI returned an empty successful response");
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(new ErrorResponse("FastAPI가 빈 응답을 반환했습니다. 첫 번째 PowerShell 로그를 확인해 주세요."));
            }
            AnalysisResponse result;
            try {
                result = JSON.readValue(responseBody, AnalysisResponse.class);
            } catch (JsonProcessingException ex) {
                LOG.error("FastAPI response was not a valid AnalysisResponse JSON", ex);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(new ErrorResponse("FastAPI의 JSON을 상품 분석 형식으로 변환하지 못했습니다. Spring Boot 로그를 확인해 주세요."));
            }
            if (result == null || result.candidates == null || result.reviewNotes == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(new ErrorResponse("AI 서비스의 응답이 비어 있습니다."));
            }
            String filename = file.getOriginalFilename();
            if (filename != null) {
                filename = filename.replaceAll("[\\\\/\\p{Cntrl}]", "_");
                if (filename.length() > 200) filename = filename.substring(0, 200);
            }
            result.recordId = records.save(bytes, mime, filename, result);
            return ResponseEntity.ok(result);
        } catch (DataAccessException ex) {
            LOG.error("Failed to persist Gemini scan", ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("사진은 분석됐지만 PostgreSQL에 저장하지 못했습니다. Spring Boot 로그와 DB 설정을 확인해 주세요."));
        } catch (RestClientResponseException ex) {
            LOG.warn("FastAPI returned HTTP {}", ex.getStatusCode());
            // FastAPI supplies short, safe Korean details; keep its status and message.
            String detail = "FastAPI 분석이 실패했습니다. 첫 번째 PowerShell 로그를 확인해 주세요.";
            try {
                JsonNode parsed = JSON.readTree(ex.getResponseBodyAsString(StandardCharsets.UTF_8));
                String upstreamDetail = parsed.path("detail").asText("");
                if (!upstreamDetail.isBlank()) detail = upstreamDetail;
            } catch (IOException ignored) {
                // Preserve the generic message if an intermediary returned non-JSON text.
            }
            return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(detail));
        } catch (ResourceAccessException ex) {
            LOG.error("Could not reach FastAPI", ex);
            if (isReadTimeout(ex)) {
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                        .body(new ErrorResponse("FastAPI 응답 대기 시간이 초과되었습니다. 첫 번째 PowerShell에서 Gemini 로그를 확인해 주세요."));
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("FastAPI 서버에 연결하지 못했습니다. 첫 번째 PowerShell과 localhost:8000/health를 확인해 주세요."));
        } catch (RestClientException ex) {
            LOG.error("FastAPI HTTP request or response processing failed", ex);
            if (isReadTimeout(ex)) {
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                        .body(new ErrorResponse("FastAPI 응답 대기 시간이 초과되었습니다. 첫 번째 PowerShell에서 Gemini 로그를 확인해 주세요."));
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponse("FastAPI 요청 또는 응답 처리에 실패했습니다. Spring Boot 로그의 예외 종류를 확인해 주세요."));
        }
    }

    private static boolean isReadTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) return true;
        }
        return false;
    }

    public static final class ErrorResponse {
        public final String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
