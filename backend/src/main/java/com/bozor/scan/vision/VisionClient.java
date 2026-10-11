package com.bozor.scan.vision;

/**
 * AI 서버(FastAPI) 호출. 설정 app.vision.mode 로 구현체를 고른다.
 * - http: HttpVisionClient — 실제 FastAPI → Gemini
 * - fake: FakeVisionClient — Gemini 없이 고정된 가짜 후보(개발·테스트·시연 연습용)
 */
public interface VisionClient {
    /** 실패하면 VisionException(재시도 가능 여부 포함)을 던진다. */
    VisionScanResult scan(VisionScanRequest request);
}
