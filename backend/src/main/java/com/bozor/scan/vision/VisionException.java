package com.bozor.scan.vision;

/**
 * AI 서버 호출 실패. code 는 scan_jobs.error_code 에 저장된다(docs/03-api.md 5.5 표).
 * retryable=true 면 워커가 available_at 을 미뤄서 다시 시도한다(30s → 60s → 120s).
 */
public class VisionException extends RuntimeException {
    public static final String RATE_LIMITED = "AI_RATE_LIMITED";
    public static final String TIMEOUT = "AI_TIMEOUT";
    public static final String UNAVAILABLE = "AI_UNAVAILABLE";
    public static final String BAD_RESPONSE = "AI_BAD_RESPONSE";
    public static final String IMAGE_INVALID = "IMAGE_INVALID";

    private final String code;
    private final boolean retryable;

    public VisionException(String code, boolean retryable, String message) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
