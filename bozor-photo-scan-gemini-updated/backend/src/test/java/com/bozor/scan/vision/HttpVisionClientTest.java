package com.bozor.scan.vision;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * AI 서버 HTTP 상태 → 스캔 오류 코드·재시도 여부(docs/03-api.md 5.5).
 * 재시도 여부가 틀리면 일시 장애에 판매자가 "분석 실패"를 보거나(재시도 안 함),
 * 고칠 수 없는 오류를 계속 다시 불러 무료 한도를 쓴다(재시도 함).
 */
class HttpVisionClientTest {

    @ParameterizedTest
    @CsvSource({
            "429, AI_RATE_LIMITED, true",
            "503, AI_UNAVAILABLE, true",    // Gemini 과부하(500·503)를 AI 서버가 503으로 넘긴다
            "504, AI_TIMEOUT, true",
            "400, IMAGE_INVALID, false",
            "413, IMAGE_INVALID, false",
            "502, AI_BAD_RESPONSE, false",
    })
    void statusMapsToErrorCodeAndRetryability(int status, String code, boolean retryable) {
        VisionException e = HttpVisionClient.fromStatus(status, null);
        assertThat(e.code()).isEqualTo(code);
        assertThat(e.retryable()).isEqualTo(retryable);
        assertThat(e.getMessage()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({"503", "429"})
    void detailFromAiServerIsShownToSeller(int status) {
        assertThat(HttpVisionClient.fromStatus(status, "Gemini 서버가 일시적으로 바쁩니다.").getMessage())
                .isEqualTo("Gemini 서버가 일시적으로 바쁩니다.");
    }
}
